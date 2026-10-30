package com.frameflow.studio.domain.identity.adapter.port;

public interface IPasswordVerifier {
    /** A null encoding still performs a password comparison to mask unknown usernames. */
    boolean matches(String password, String encoding);
    String encode(String password);
}
