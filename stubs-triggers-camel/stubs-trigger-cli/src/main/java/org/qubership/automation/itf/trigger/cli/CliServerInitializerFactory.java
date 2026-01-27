/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.trigger.cli;

import org.apache.camel.component.netty4.DefaultServerInitializerFactory;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.ServerInitializerFactory;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public class CliServerInitializerFactory extends DefaultServerInitializerFactory {

    ConnectionProperties connectionProperties;

    public CliServerInitializerFactory(NettyConsumer consumer, ConnectionProperties connectionProperties) {
        super(consumer);
        this.connectionProperties = connectionProperties;
    }

    @Override
    public ServerInitializerFactory createPipelineFactory(NettyConsumer consumer) {
        return new CliServerInitializerFactory(consumer, this.connectionProperties);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        super.initChannel(ch);
        ChannelPipeline channelPipeline = ch.pipeline();
        String greeting = connectionProperties.getOrDefault(CliConstants.Inbound.GREETING, StringUtils.EMPTY)
                .toString();
        String formattedGreeting = greeting
                .replace("%n", System.lineSeparator())
                .replace("%r", "\r");
        channelPipeline.writeAndFlush(formattedGreeting);
    }
}
