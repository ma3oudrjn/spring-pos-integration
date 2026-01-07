package com.dradev.clinic.models.sap;

import lombok.Data;

@Data
public class TokenResponse {

    private String access_token;
    private String token_type;
    private long expires_in;
    private String refresh_token;
    private String scope;
}
