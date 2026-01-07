import io.swagger.annotations.Api;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "manage-pc-pos")
@RequestMapping({"/manage/pc-pos/payments"})
@AllArgsConstructor
public class SapManageController {

    private final SapService sapService;

    @GetMapping("/token")
    public ResponseEntity<?> getToken() {
        TokenResponse token = sapService.getToken();
        return ResponseEntity.ok(token);
    }

    @PostMapping("/identifier")
    public ResponseEntity<?> receiveIdentifier() {
        IdentifierResponse id = sapService.receiveIdentifier();
        return ResponseEntity.ok(id);
    }

    @PostMapping("/start")
    public ResponseEntity<?> startPayment(@RequestBody StartPaymentRequestDto req) {
        ResponseModel<?> resp = sapService.startPayment(req);
        return ResponseEntity.ok(resp);
    }


    @PostMapping("/cancellation")
    public ResponseEntity<?> inquery(@RequestBody InqueryRequestDto req) {
        ResponseModel<InqueryDataDto> resp = sapService.inquery(req);
        return ResponseEntity.ok(resp);
    }
}
