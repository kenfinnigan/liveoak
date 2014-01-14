package io.liveoak.container.deploy;

import io.liveoak.spi.ResourceResponse;
import io.liveoak.spi.resource.config.ConfigResource;
import io.liveoak.spi.resource.RootResource;
import io.liveoak.spi.resource.async.Resource;
import io.liveoak.spi.state.ResourceState;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * @author Bob McWhirter
 */
public class ConfigurationWatcher extends ChannelOutboundHandlerAdapter {

    public ConfigurationWatcher(DirectoryDeploymentManager deploymentManager) {
        this.deploymentManager = deploymentManager;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if ( msg instanceof ResourceResponse) {
            if ( ((ResourceResponse) msg).responseType().equals( ResourceResponse.ResponseType.UPDATED ) ) {
                Resource current = ((ResourceResponse) msg).resource();
                ResourceState configResourceState = null;
                RootResource rootResource = null;

                while ( current != null ) {
                    if ( current instanceof ConfigResource) {
                        configResourceState = ((ResourceResponse)msg).state();
                        break;
                    }
                    current = current.parent();
                }

                while ( current != null ) {
                    if ( current instanceof RootResource) {
                        rootResource = (RootResource) current;
                        break;
                    }
                    current = current.parent();
                }

                if ( configResourceState != null ) {
                    this.deploymentManager.updateConfiguration(rootResource, configResourceState);
                }
            }
        }
        super.write(ctx, msg, promise);
    }

    private DirectoryDeploymentManager deploymentManager;
}
