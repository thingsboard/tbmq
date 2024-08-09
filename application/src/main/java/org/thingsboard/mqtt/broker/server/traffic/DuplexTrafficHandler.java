package org.thingsboard.mqtt.broker.server.traffic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.util.BytesUtil;

@Slf4j
@RequiredArgsConstructor
public class DuplexTrafficHandler extends ChannelDuplexHandler {

    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final boolean isTlsConnection;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        reportTraffic(msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        reportTraffic(msg);
        ctx.write(msg, promise);
    }

    private void reportTraffic(Object msg) {
        if (msg instanceof ByteBuf byteBuf) {
            tbMessageStatsReportClient.reportTraffic(BytesUtil.getPacketSize(byteBuf, isTlsConnection));
        }
    }

}
