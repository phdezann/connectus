package org.connectus.dagger;

import dagger.Component;

import javax.inject.Singleton;

@Component(modules = {AndroidModule.class, AppModule.class})
@Singleton
public interface ConnectusMainComponent extends ConnectusComponent {}
