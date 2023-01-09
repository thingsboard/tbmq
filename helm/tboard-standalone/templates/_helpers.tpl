{{/*
Expand the name of the chart.
*/}}
{{- define "tboard.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "tboard.selectorLabels" -}}
app: {{ include "tboard.name" . }}
{{- end }}
