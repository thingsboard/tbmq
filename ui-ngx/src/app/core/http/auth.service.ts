///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Injectable } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { HttpClient } from '@angular/common/http';

import { forkJoin, Observable, of, ReplaySubject, throwError } from 'rxjs';
import { catchError, mergeMap, tap } from 'rxjs/operators';

import { LoginRequest, LoginResponse, PublicLoginRequest } from '@shared/models/login.models';
import { Router, UrlTree } from '@angular/router';
import { defaultHttpOptions, defaultHttpOptionsFromConfig, RequestConfig } from '../http/http-utils';
import { Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { Authority } from '@shared/models/authority.enum';
import { ActionSettingsChangeLanguage } from '@app/core/settings/settings.actions';
import { AuthPayload, AuthState } from '@core/auth/auth.models';
import { TranslateService } from '@ngx-translate/core';
import { AuthUser, User } from '@shared/models/user.model';
import { UtilsService } from '@core/services/utils.service';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { AlertDialogComponent } from '@shared/components/dialog/alert-dialog.component';
import { ActionAuthAuthenticated, ActionAuthLoadUser, ActionAuthUnauthenticated } from '@core/auth/auth.actions';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { UserPasswordPolicy } from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  constructor(private store: Store<AppState>,
              private http: HttpClient,
              private settingsService: SettingsService,
              private router: Router,
              private utils: UtilsService,
              private translate: TranslateService,
              private dialog: MatDialog) {
  }

  redirectUrl: string;

  private refreshTokenSubject: ReplaySubject<LoginResponse> = null;
  private jwtHelper = new JwtHelperService();

  private static _storeGet(key) {
    return localStorage.getItem(key);
  }

  private static isTokenValid(prefix) {
    const clientExpiration = AuthService._storeGet(prefix + '_expiration');
    return clientExpiration && Number(clientExpiration) > (new Date().valueOf() + 2000);
  }

  public static isJwtTokenValid() {
    return AuthService.isTokenValid('jwt_token');
  }

  private static clearTokenData() {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('jwt_token_expiration');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('refresh_token_expiration');
  }

  public static getJwtToken() {
    return AuthService._storeGet('jwt_token');
  }

  public getUser(config?: RequestConfig): Observable<User> {
    return this.http.get<User>(`/api/auth/user`, defaultHttpOptionsFromConfig(config));
  }

  public reloadUser() {
    this.loadUser(true).subscribe(
      (authPayload) => {
        this.notifyAuthenticated(authPayload);
        this.notifyUserLoaded(true);
      },
      () => {
        this.notifyUnauthenticated();
        this.notifyUserLoaded(true);
      }
    );
  }

  public login(loginRequest: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', loginRequest, defaultHttpOptions()).pipe(
      tap((loginResponse: LoginResponse) => {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, true);
        }
      ));
  }

  public publicLogin(publicId: string): Observable<LoginResponse> {
    const publicLoginRequest: PublicLoginRequest = {
      publicId
    };
    return this.http.post<LoginResponse>('/api/auth/login/public', publicLoginRequest, defaultHttpOptions());
  }

  public sendResetPasswordLink(email: string) {
    return this.http.post('/api/noauth/resetPasswordByEmail',
      {email}, defaultHttpOptions());
  }

  public activate(activateToken: string, password: string, sendActivationMail: boolean): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`/api/noauth/activate?sendActivationMail=${sendActivationMail}`,
      {activateToken, password}, defaultHttpOptions()).pipe(
      tap((loginResponse: LoginResponse) => {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, true);
        }
      ));
  }

  public resetPassword(resetToken: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/noauth/resetPassword', {resetToken, password}, defaultHttpOptions()).pipe(
      tap((loginResponse: LoginResponse) => {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, true);
        }
      ));
  }

  public changePassword(currentPassword: string, newPassword: string, config?: RequestConfig) {
    return this.http.post('/api/auth/changePassword', {currentPassword, newPassword}, defaultHttpOptionsFromConfig(config)).pipe(
      tap((loginResponse: LoginResponse) => {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, false);
        }
      ));
  }

  public activateByEmailCode(emailCode: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`/api/noauth/activateByEmailCode?emailCode=${emailCode}`,
      null, defaultHttpOptions());
  }

  public resendEmailActivation(email: string) {
    const encodeEmail = encodeURIComponent(email);
    return this.http.post(`/api/noauth/resendEmailActivation?email=${encodeEmail}`,
      null, defaultHttpOptions());
  }

  public loginAsUser(userId: string) {
    return this.http.get<LoginResponse>(`/api/admin/user/${userId}/token`, defaultHttpOptions()).pipe(
      tap((loginResponse: LoginResponse) => {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, true);
        }
      ));
  }

  public logout(captureLastUrl: boolean = false) {
    if (captureLastUrl) {
      this.redirectUrl = this.router.url;
    }
    this.clearJwtToken();
  }

  private notifyUserLoaded(isUserLoaded: boolean) {
    this.store.dispatch(new ActionAuthLoadUser({isUserLoaded}));
  }

  public defaultUrl(isAuthenticated: boolean, authState?: AuthState, path?: string, params?: any): UrlTree {
    let result: UrlTree = null;
    if (isAuthenticated) {
      if (!path || path === 'login') {
        if (this.redirectUrl) {
          const redirectUrl = this.redirectUrl;
          this.redirectUrl = null;
          result = this.router.parseUrl(redirectUrl);
        } else {
          result = this.router.parseUrl('home');
        }
      }
    } else {
      result = this.router.parseUrl('login');
    }
    return result;
  }

  private loadUser(doTokenRefresh): Observable<AuthPayload> {
    const authUser = getCurrentAuthUser(this.store);
    if (!authUser) {
      const publicId = this.utils.getQueryParam('publicId');
      const accessToken = this.utils.getQueryParam('accessToken');
      const refreshToken = this.utils.getQueryParam('refreshToken');
      const username = this.utils.getQueryParam('username');
      const password = this.utils.getQueryParam('password');
      const loginError = this.utils.getQueryParam('loginError');
      if (publicId) {
        return this.publicLogin(publicId).pipe(
          mergeMap((response) => {
            this.updateAndValidateTokens(response.token, response.refreshToken, false);
            return this.procceedJwtTokenValidate();
          }),
          catchError((err) => {
            this.utils.updateQueryParam('publicId', null);
            throw Error();
          })
        );
      } else if (accessToken) {
        const queryParamsToRemove = ['accessToken'];
        if (refreshToken) {
          queryParamsToRemove.push('refreshToken');
        }
        this.utils.removeQueryParams(queryParamsToRemove);
        try {
          this.updateAndValidateToken(accessToken, 'jwt_token', false);
          if (refreshToken) {
            this.updateAndValidateToken(refreshToken, 'refresh_token', false);
          } else {
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('refresh_token_expiration');
          }
        } catch (e) {
          return throwError(e);
        }
        return this.procceedJwtTokenValidate();
      } else if (username && password) {
        this.utils.updateQueryParam('username', null);
        this.utils.updateQueryParam('password', null);
        const loginRequest: LoginRequest = {
          username,
          password
        };
        return this.http.post<LoginResponse>('/api/auth/login', loginRequest, defaultHttpOptions()).pipe(
          mergeMap((loginResponse: LoginResponse) => {
              this.updateAndValidateTokens(loginResponse.token, loginResponse.refreshToken, false);
              return this.procceedJwtTokenValidate();
            }
          )
        );
      } else if (loginError) {
        this.showLoginErrorDialog(loginError);
        this.utils.updateQueryParam('loginError', null);
        return throwError(Error());
      }
      return this.procceedJwtTokenValidate(doTokenRefresh);
    } else {
      return of({} as AuthPayload);
    }
  }

  private showLoginErrorDialog(loginError: string) {
    this.translate.get(['login.error', 'action.close']).subscribe(
      (translations) => {
        const dialogConfig: MatDialogConfig = {
          disableClose: true,
          data: {
            title: translations['login.error'],
            message: loginError,
            ok: translations['action.close']
          }
        };
        this.dialog.open(AlertDialogComponent, dialogConfig);
      }
    );
  }

  private procceedJwtTokenValidate(doTokenRefresh?: boolean): Observable<AuthPayload> {
    const loadUserSubject = new ReplaySubject<AuthPayload>();
    forkJoin([
      this.validateJwtToken(doTokenRefresh),
      this.settingsService.getConnectivitySettings()
    ]).subscribe(
      () => {
        const authPayload = {} as AuthPayload;
        const jwtToken = AuthService._storeGet('jwt_token');
        authPayload.authUser = this.jwtHelper.decodeToken(jwtToken);
        if (authPayload.authUser && authPayload.authUser.scopes && authPayload.authUser.scopes.length) {
          authPayload.authUser.authority = Authority[authPayload.authUser.scopes[0]];
        }
        if (authPayload.authUser.userId) {
          this.getUser().subscribe(
            (user) => {
              if (user) {
                authPayload.userDetails = user;
                let userLang;
                if (authPayload.userDetails.additionalInfo && authPayload.userDetails.additionalInfo.lang) {
                  userLang = authPayload.userDetails.additionalInfo.lang;
                } else {
                  userLang = null;
                }
                this.notifyUserLang(userLang);
                loadUserSubject.next(authPayload);
                loadUserSubject.complete();
              } else {
                this.clearJwtToken();
              }
            },
            (err) => {
              loadUserSubject.error(err);
              this.logout();
            }
          );
        } else {
          loadUserSubject.error(null);
        }
      },
      (err) => {
        loadUserSubject.error(err);
      }
    );
    return loadUserSubject;
  }

  public loadIsUserTokenAccessEnabled(authUser: AuthUser): Observable<boolean> {
    if (authUser.authority === Authority.SYS_ADMIN) {
      return this.http.get<boolean>('/api/admin/user/tokenAccessEnabled', defaultHttpOptions());
    } else {
      return of(false);
    }
  }

  public loadIsEdgesSupportEnabled(): Observable<boolean> {
    return this.http.get<boolean>('/api/edges/enabled', defaultHttpOptions());
  }

  public refreshJwtToken(loadUserElseStoreJwtToken = true): Observable<LoginResponse> {
    let response: Observable<LoginResponse> = this.refreshTokenSubject;
    if (this.refreshTokenSubject === null) {
      this.refreshTokenSubject = new ReplaySubject<LoginResponse>(1);
      response = this.refreshTokenSubject;
      const refreshToken = AuthService._storeGet('refresh_token');
      const refreshTokenValid = AuthService.isTokenValid('refresh_token');
      this.setUserFromJwtToken(null, null, false);
      if (!refreshTokenValid) {
        this.translate.get('access.refresh-token-expired').subscribe(
          (translation) => {
            this.refreshTokenSubject.error(new Error(translation));
            this.refreshTokenSubject = null;
          }
        );
      } else {
        const refreshTokenRequest = {
          refreshToken
        };
        const refreshObservable = this.http.post<LoginResponse>('/api/auth/token', refreshTokenRequest, defaultHttpOptions());
        refreshObservable.subscribe((loginResponse: LoginResponse) => {
          if (loadUserElseStoreJwtToken) {
            this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, false);
          } else {
            this.updateAndValidateTokens(loginResponse.token, loginResponse.refreshToken, true);
          }
          this.refreshTokenSubject.next(loginResponse);
          this.refreshTokenSubject.complete();
          this.refreshTokenSubject = null;
        }, () => {
          this.clearJwtToken();
          this.refreshTokenSubject.error(new Error(this.translate.instant('access.refresh-token-failed')));
          this.refreshTokenSubject = null;
        });
      }
    }
    return response;
  }

  private validateJwtToken(doRefresh): Observable<void> {
    const subject = new ReplaySubject<void>();
    if (!AuthService.isTokenValid('jwt_token')) {
      if (doRefresh) {
        this.refreshJwtToken(!doRefresh).subscribe(
          () => {
            subject.next();
            subject.complete();
          },
          (err) => {
            subject.error(err);
          }
        );
      } else {
        this.clearJwtToken();
        subject.error(null);
      }
    } else {
      subject.next();
      subject.complete();
    }
    return subject;
  }

  public refreshTokenPending() {
    return this.refreshTokenSubject !== null;
  }

  public setUserFromJwtToken(jwtToken, refreshToken, notify): Observable<boolean> {
    const authenticatedSubject = new ReplaySubject<boolean>();
    if (!jwtToken) {
      AuthService.clearTokenData();
      if (notify) {
        this.notifyUnauthenticated();
      }
      authenticatedSubject.next(false);
      authenticatedSubject.complete();
    } else {
      this.updateAndValidateTokens(jwtToken, refreshToken, true);
      if (notify) {
        this.notifyUserLoaded(false);
        this.loadUser(false).subscribe(
          (authPayload) => {
            this.notifyUserLoaded(true);
            this.notifyAuthenticated(authPayload);
            authenticatedSubject.next(true);
            authenticatedSubject.complete();
          },
          () => {
            this.notifyUserLoaded(true);
            this.notifyUnauthenticated();
            authenticatedSubject.next(false);
            authenticatedSubject.complete();
          }
        );
      } else {
        this.loadUser(false).subscribe(
          () => {
            authenticatedSubject.next(true);
            authenticatedSubject.complete();
          },
          () => {
            authenticatedSubject.next(false);
            authenticatedSubject.complete();
          }
        );
      }
    }
    return authenticatedSubject;
  }

  private updateAndValidateTokens(jwtToken, refreshToken, notify: boolean) {
    this.updateAndValidateToken(jwtToken, 'jwt_token', notify);
    this.updateAndValidateToken(refreshToken, 'refresh_token', notify);
  }

  public parsePublicId(): string {
    const token = AuthService.getJwtToken();
    if (token) {
      const tokenData = this.jwtHelper.decodeToken(token);
      if (tokenData && tokenData.isPublic) {
        return tokenData.sub;
      }
    }
    return null;
  }

  private notifyUnauthenticated() {
    this.store.dispatch(new ActionAuthUnauthenticated());
  }

  private notifyAuthenticated(authPayload: AuthPayload) {
    this.store.dispatch(new ActionAuthAuthenticated(authPayload));
  }

  private notifyUserLang(userLang: string) {
    this.store.dispatch(new ActionSettingsChangeLanguage({userLang}));
  }

  private updateAndValidateToken(token, prefix, notify) {
    let valid = false;
    const tokenData = this.jwtHelper.decodeToken(token);
    const issuedAt = tokenData.iat;
    const expTime = tokenData.exp;
    if (issuedAt && expTime) {
      const ttl = expTime - issuedAt;
      if (ttl > 0) {
        const clientExpiration = new Date().valueOf() + ttl * 1000;
        localStorage.setItem(prefix, token);
        localStorage.setItem(prefix + '_expiration', '' + clientExpiration);
        valid = true;
      }
    }
    if (!valid && notify) {
      this.notifyUnauthenticated();
    }
  }

  private clearJwtToken() {
    this.setUserFromJwtToken(null, null, true);
  }

  public getUserPasswordPolicy(config?: RequestConfig) {
    return this.http.get<UserPasswordPolicy>(`/api/noauth/userPasswordPolicy`, defaultHttpOptionsFromConfig(config));
  }

}
