package com.dradev.clinic.services.inter;

import com.dradev.clinic.models.sap.*;

public interface SapService {

    TokenResponse getToken();

    IdentifierResponse receiveIdentifier();

    ResponseModel<?> startPayment(StartPaymentRequestDto request);

    ResponseModel<InqueryDataDto> inquery(InqueryRequestDto request);
}
