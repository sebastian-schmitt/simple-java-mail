package org.simplejavamail.internal.modules;

import org.simplejavamail.api.internal.authenticatedsockssupport.socks5server.AnonymousSocks5Server;
import org.simplejavamail.api.mailer.config.ProxyConfig;

import org.jetbrains.annotations.NotNull;

public interface AuthenticatedSocksModule {

	String NAME = "Authenticated socks module";

	AnonymousSocks5Server createAnonymousSocks5Server(@NotNull ProxyConfig socksProxyConfig);
}