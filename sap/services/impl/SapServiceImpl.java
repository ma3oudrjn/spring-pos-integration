package com.dradev.clinic.services.impl;

import com.dradev.clinic.models.sap.*;
import com.dradev.clinic.services.inter.SapService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SapServiceImpl implements SapService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public class SapException extends RuntimeException {
        public SapException(String message) {
            super(message);
        }

        public SapException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${sap.idn.token-url}")
    private String tokenUrl;

    @Value("${sap.api.base-url}")
    private String apiBaseUrl;

    @Value("${sap.client.basic-authorization}")
    private String basicAuth;

    @Value("${sap.ro.username}")
    private String roUsername;

    @Value("${sap.ro.password}")
    private String roPassword;

    @Value("${sap.terminal-id}")
    private String terminalId;

    private volatile TokenResponse cachedToken;
    private volatile Instant tokenExpiryTime = Instant.EPOCH;

    @Override
    public synchronized TokenResponse getToken() {
        // check cache
        if (cachedToken != null && Instant.now().isBefore(tokenExpiryTime.minusSeconds(10))) {
            return cachedToken;
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("grant_type", "password");
            params.put("username", roUsername);
            params.put("password", roPassword);
            // scope can be adjusted or read from properties
            params.put("scope", "SepCentralPcPos openid");

            String body = params.entrySet().stream().map(e -> {
                try {
                    return URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.toString()) + "=" +
                            URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8.toString());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).reduce((a, b) -> a + "&" + b).orElse("");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.error("Token request failed: {} -> {}", resp.statusCode(), resp.body());
                throw new SapException("Failed to get token: " + resp.body());
            }

            TokenResponse token = objectMapper.readValue(resp.body(), TokenResponse.class);
            cachedToken = token;
            tokenExpiryTime = Instant.now().plusSeconds(token.getExpires_in());
            return token;
        } catch (IOException | InterruptedException e) {
            throw new SapException("Error while requesting token", e);
        }
    }

    private String bearer() {
        TokenResponse token = getToken();
        return token.getToken_type() + " " + token.getAccess_token();
    }

    @Override
    public IdentifierResponse receiveIdentifier() {
        try {
            String url = apiBaseUrl + "/v1/PcPosTransaction/ReciveIdentifier";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", bearer())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            log.info("ReciveIdentifier status={}, body={}", resp.statusCode(), resp.body());

            if (resp.statusCode() != 200) {
                throw new SapException("ReciveIdentifier failed: " + resp.body());
            }

            JsonNode root = objectMapper.readTree(resp.body());
            boolean isSuccess = root.path("IsSuccess").asBoolean(false);
            JsonNode dataNode = root.path("Data");
            String identifier = dataNode.path("Identifier").asText(null);

            if (isSuccess && identifier != null) {
                IdentifierResponse result = new IdentifierResponse();
                result.setIdentifier(identifier);
                return result;
            } else {
                throw new SapException("ReciveIdentifier returned invalid data: " + resp.body());
            }

        } catch (IOException | InterruptedException e) {
            throw new SapException("Error in receiveIdentifier: " + e.getMessage(), e);
        }
    }

    @Override
    public ResponseModel<JsonNode> startPayment(StartPaymentRequestDto request) {
        try {
            SepPcPosRequest sepRequest = new SepPcPosRequest();
            sepRequest.setTerminalID(terminalId);
            sepRequest.setAccountType(0);
            sepRequest.setTransactionType(0);
            sepRequest.setIdentifier(request.getIdentifier());
            sepRequest.setAmount(String.valueOf(request.getAmount()));

            UserNotifiable userNotifiable = new UserNotifiable();
            userNotifiable.setFooterMessage("باتشکر");
            PrintItem item = new PrintItem();
            item.setAlignment(0);
            item.setItem("");
            item.setReceiptType(0);
            item.setValue("");
            userNotifiable.setPrintItems(List.of(item));
            sepRequest.setUserNotifiable(userNotifiable);

            String jsonBody = objectMapper.writeValueAsString(sepRequest);
            log.info("StartPayment JSON = {}", jsonBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/v1/PcPosTransaction/StartPayment"))
                    .header("Authorization", bearer())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            log.info("StartPayment HTTP {} -> {}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                throw new SapException("StartPayment failed HTTP " + response.statusCode() + " body=" + response.body());
            }

            String respBody = response.body();
            if (respBody == null || respBody.isBlank()) {
                throw new SapException("StartPayment empty response body");
            }

            JsonNode root = objectMapper.readTree(respBody);

            boolean isSuccess = root.has("IsSuccess") ? root.get("IsSuccess").asBoolean(false)
                    : root.path("isSuccess").asBoolean(false);

            int errorCode = root.path("ErrorCode").asInt(0);
            String errorDescription = root.path("ErrorDescription").isNull() ? null : root.path("ErrorDescription").asText();

            if (!isSuccess) {
                throw new SapException("Central POS rejected StartPayment: " + errorCode + " - " + errorDescription);
            }

            JsonNode dataNode = root.has("Data") ? root.get("Data") : null;
            JsonNode parsedData = null;

            if (dataNode != null && !dataNode.isNull()) {
                if (dataNode.isTextual()) {
                    String dataStr = dataNode.asText();

                    // پاک‌کردن کاراکترهای null که ممکنه باعث crash بشن
                    if (dataStr.indexOf('\u0000') >= 0) {
                        dataStr = dataStr.replace("\u0000", "");
                        log.warn("Data string contained null chars — they were removed before parsing");
                    }

                    dataStr = dataStr.trim();

                    // تلاش برای parse کردن رشته به JSON
                    try {
                        parsedData = objectMapper.readTree(dataStr);
                    } catch (Exception ex) {
                        log.warn("Failed to parse Data text as JSON — returning raw string in 'raw' field", ex);
                        // بازگرداندن یک object امن با فیلد raw (تا فرانت اند کرش نکند)
                        parsedData = objectMapper.createObjectNode().put("raw", dataStr);
                    }
                } else if (dataNode.isObject() || dataNode.isArray()) {
                    // اگر از اول JSON بود، مستقیم استفاده کن
                    parsedData = dataNode;
                } else {
                    // هر چیز دیگه‌ای (مثلاً عدد یا boolean) رو به رشته تبدیل کن
                    parsedData = objectMapper.createObjectNode().put("value", dataNode.asText());
                }
            } else {
                // اگر Data نبود، ممکنه بعضی فیلدها مستقیماً توی root باشند — برای اطمینان لاگ کن
                log.info("StartPayment: Data is null or missing in response. root = {}", root.toString());
            }

            ResponseModel<JsonNode> serverResp = new ResponseModel<>();
            serverResp.setSuccess(true);
            serverResp.setErrorCode(errorCode);
            serverResp.setErrorDescription(errorDescription);
            serverResp.setData(parsedData);

            return serverResp;

        } catch (SapException se) {
            throw se;
        } catch (Exception e) {
            log.error("StartPayment unexpected error", e);
            throw new SapException("StartPayment failed: " + e.getMessage(), e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public class SepPcPosRequest {

        @JsonProperty("TerminalID")
        private String terminalID;

        @JsonProperty("accountType")
        private Integer accountType;

        @JsonProperty("transactionType")
        private Integer transactionType;

        @JsonProperty("ResNum")
        private String resNum;

        @JsonProperty("Identifier")
        private String identifier;

        @JsonProperty("Amount")
        private String amount;

        @JsonProperty("userNotifiable")
        private UserNotifiable userNotifiable;

        public String getTerminalID() {
            return terminalID;
        }

        public void setTerminalID(String terminalID) {
            this.terminalID = terminalID;
        }

        public Integer getAccountType() {
            return accountType;
        }

        public void setAccountType(Integer accountType) {
            this.accountType = accountType;
        }

        public Integer getTransactionType() {
            return transactionType;
        }

        public void setTransactionType(Integer transactionType) {
            this.transactionType = transactionType;
        }

        public String getResNum() {
            return resNum;
        }

        public void setResNum(String resNum) {
            this.resNum = resNum;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public UserNotifiable getUserNotifiable() {
            return userNotifiable;
        }

        public void setUserNotifiable(UserNotifiable userNotifiable) {
            this.userNotifiable = userNotifiable;
        }
    }

    public class UserNotifiable {

        @JsonProperty("footerMessage")
        private String footerMessage;

        @JsonProperty("printItems")
        private List<PrintItem> printItems;

        public String getFooterMessage() {
            return footerMessage;
        }

        public void setFooterMessage(String footerMessage) {
            this.footerMessage = footerMessage;
        }

        public List<PrintItem> getPrintItems() {
            return printItems;
        }

        public void setPrintItems(List<PrintItem> printItems) {
            this.printItems = printItems;
        }
    }


    public class PrintItem {

        @JsonProperty("alignment")
        private Integer alignment;

        @JsonProperty("item")
        private String item;

        @JsonProperty("receiptType")
        private Integer receiptType;

        @JsonProperty("value")
        private String value;

        public Integer getAlignment() {
            return alignment;
        }

        public void setAlignment(Integer alignment) {
            this.alignment = alignment;
        }

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public Integer getReceiptType() {
            return receiptType;
        }

        public void setReceiptType(Integer receiptType) {
            this.receiptType = receiptType;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Override
    public ResponseModel<InqueryDataDto> inquery(InqueryRequestDto request) {
        try {
            request.setTerminalID(terminalId);
            request.setCancelPendingRequest(true);
            String json = objectMapper.writeValueAsString(request);
            log.info("Inquery JSON = {}", json);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/v1/PcPosTransaction/Inquery"))
                    .header("Authorization", bearer())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());
            if (response.statusCode() != 200) {
                throw new SapException(
                        "Inquery failed. status=" +
                                response.statusCode() +
                                " body=" +
                                response.body()
                );
            }

            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<ResponseModel<InqueryDataDto>>() {}
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new SapException("Error in inquery", e);
        }
    }

}
