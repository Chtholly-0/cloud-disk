import com.blackcat.common.config.template.SmsTemplate;
import com.blackcat.web.CloudDiskApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Jason
 * @date 2022/5/11
 * hello ashen one
 */
@SpringBootTest(classes = CloudDiskApplication.class)
@RunWith(SpringRunner.class)
public class sendMsgTest {
    @Test
    public void test(){
        SmsTemplate.SendSms("18523737141","888888");
    }
}
