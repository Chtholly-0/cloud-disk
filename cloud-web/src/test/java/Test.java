import cn.hutool.crypto.SecureUtil;

import java.io.File;

public class Test {
    public static void main(String[] args) {

        File file = new File("C:\\IDEA_workplace\\SpringBoot-package\\cloud-disk\\file-space\\2dd\\2ddabb1b97470ed2b6ad102ec95cfdd8\\96335e594b9549b9800e19afffb3ad70.pdf");
        System.out.println(SecureUtil.md5(file));
    }
}
