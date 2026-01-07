package com.dradev.clinic.models.sap;

import lombok.Data;

@Data
public class UserNotifiableItem {

    private String Item;
    private String Value;
    private Integer Alignment;
    private Integer ReceiptType;
}
