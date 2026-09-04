package com.legacyminecraft.bungeeposeidon.login;

@FunctionalInterface
public interface LoginStage {
    void run(LoginProcessHandler loginProcessHandler);
}
