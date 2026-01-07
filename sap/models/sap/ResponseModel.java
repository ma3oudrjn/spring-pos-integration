package com.dradev.clinic.models.sap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseModel<T> {
    private boolean isSuccess;
    private int errorCode;
    private String errorDescription;
    private T data;
}
