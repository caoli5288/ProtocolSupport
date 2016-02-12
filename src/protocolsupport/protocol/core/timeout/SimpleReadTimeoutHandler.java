package protocolsupport.protocol.core.timeout;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;

public class SimpleReadTimeoutHandler extends ChannelInboundHandlerAdapter {

	private volatile ScheduledFuture<?> timeoutTask;
	protected final long timeoutTime;
	protected long lastReadTime;

	public SimpleReadTimeoutHandler(int timeout) {
		this(timeout, TimeUnit.SECONDS);
	}

	public SimpleReadTimeoutHandler(long timeout, TimeUnit tu) {
		this.timeoutTime = tu.toMillis(timeout);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		this.initialize(ctx);
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		this.destroy();
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
		this.lastReadTime = System.currentTimeMillis();
		ctx.fireChannelRead(message);
	}

	private void initialize(final ChannelHandlerContext ctx) {
		this.lastReadTime = System.currentTimeMillis();
		this.timeoutTask = ctx.executor().schedule(new Runnable() {
			@Override
			public void run() {
				if (ctx.channel().isOpen()) {
					long untilTimeout = timeoutTime - (System.currentTimeMillis() - lastReadTime);
					if (untilTimeout <= 0) {
						try {
							readTimedOut(ctx);
						} catch (Throwable e) {
							ctx.fireExceptionCaught(e);
						}
					} else {
						ctx.executor().schedule(this, untilTimeout, TimeUnit.MILLISECONDS);
					}
				}
			}
		}, this.timeoutTime, TimeUnit.MILLISECONDS);
	}

	private void destroy() {
		if (this.timeoutTask != null) {
			this.timeoutTask.cancel(false);
			this.timeoutTask = null;
		}
	}

	protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
		ctx.fireExceptionCaught(ReadTimeoutException.getInstance());
		ctx.close();
	}

}
