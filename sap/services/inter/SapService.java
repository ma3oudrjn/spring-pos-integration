
public interface SapService {

    TokenResponse getToken();

    IdentifierResponse receiveIdentifier();

    ResponseModel<?> startPayment(StartPaymentRequestDto request);

    ResponseModel<InqueryDataDto> inquery(InqueryRequestDto request);
}
