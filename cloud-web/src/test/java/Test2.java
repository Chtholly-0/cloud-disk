import cn.hutool.core.util.StrUtil;

public class Test2 {
    public static void main(String[] args) {
//        String fileName = "\\\\\\\\\\\\\\\\";
//        for (char c : fileName.toCharArray()) {
//            if (c == '/' || c == '\\' || c == '"' || c == ':' || c == '|' || c == '*' || c == '?' || c == '<' || c == '>'){
//                System.out.println("未匹配");
//                break;
//            }
//        }
//        System.out.println("???????????");
        String str = "           ";
        char[] chs = str.toCharArray();
        int l = 0, r = chs.length - 1;
        while (l < r) {
            if (chs[l] != ' ' && chs[r] != ' ') {
                break;
            }
            if (chs[l] == ' ') {
                l++;
            }
            if (chs[r] == ' ') {
                r--;
            }
        }
        if (l >= r) {
            System.out.println("空白");
        }
        String sub = str.substring(l, r + 1);
        System.out.println("|" + sub + "|");
    }
}
