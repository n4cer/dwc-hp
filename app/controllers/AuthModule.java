package controllers;

import com.google.inject.AbstractModule;

/**
 * Registered via play.modules.enabled so AuthBootstrap runs on startup even though
 * nothing else injects it directly.
 */
public final class AuthModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AuthBootstrap.class).asEagerSingleton();
    }
}
