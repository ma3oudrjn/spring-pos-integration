package com.dradev.clinic.models.sap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class StartPaymentRequestDto {

    @JsonProperty("Identifier")
    @NotBlank
    private String identifier;

    @JsonProperty("Amount")
    @NotBlank
    private String amount;

    @JsonIgnore
    private String terminalID;

    @JsonIgnore
    private Integer accountType;

    @JsonIgnore
    private Integer transactionType;

    private String resNum;

    private String refrenceData;

    @JsonIgnore
    private UserNotifiable userNotifiable;
}
