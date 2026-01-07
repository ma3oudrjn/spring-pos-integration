
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InqueryRequestDto {

    private String identifier;

    @JsonProperty(value = "CancelPendingRequest", access = JsonProperty.Access.READ_ONLY)
    private Boolean cancelPendingRequest = true;

    @JsonProperty(value = "TerminalID", access = JsonProperty.Access.READ_ONLY)
    private String terminalID;
}
