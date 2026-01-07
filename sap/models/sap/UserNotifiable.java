
import lombok.Data;
import java.util.List;

@Data
public class UserNotifiable {

    private String FooterMessage;
    private List<UserNotifiableItem> PrintItems;
}
