package com.dradev.clinic.models.sap;

import lombok.Data;
import java.util.List;

@Data
public class UserNotifiable {

    private String FooterMessage;
    private List<UserNotifiableItem> PrintItems;
}
