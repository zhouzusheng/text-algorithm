package org.text.algorithm;

import org.text.algorithm.regexp.Automaton;
import org.text.algorithm.regexp.AutomatonBuilder;
import org.text.algorithm.regexp.AutomatonMatcher;
import org.text.algorithm.regexp.fsm.NFAHelper;
import org.text.algorithm.regexp.fsm.PikeNFA;
import org.text.algorithm.regexp.vm.CharacterParser;
import org.text.algorithm.regexp.vm.PikeCompiler;
import org.text.algorithm.regexp.vm.PikeVM;
import org.junit.Test;

import java.io.*;

public class RegExpTest {
    static String[] regexp = {
            "提\\h{1,2}问",
            "\\sLOGIN\\s[^\\n]*?\\s\\{",
            "\\sLOGIN\\s[^\\n]{100}",
            "\\sLOGIN\\s[^\\n]*?%",
            "\\sLOGIN\\s\\w+\\s\\{\\d+\\}[\\r]?\\n[^\\n]*?%",
            "\\sAUTHENTICATE\\s[^\\n]*?\\{",
            "\\sAUTHENTICATE\\s[^\\n]{100}",
            "\\sDELETE\\s[^\\n]{100}",
            "\\sDELETE\\s[^\\n]*?\\{",
            "\\sCOPY\\s[^\\n]*?\\{",
            "(\\{(?=\\d+\\}[^\\n]*?\\sAUTH)|AUTH\\s[^\\n]*?\\{(?=\\d+\\}))",
            "AUTH\\s[^\\n]{100}",
            "\\sLSUB\\s[^\\n]*?\\s\\{",
            "PARTIAL.*BODY\\.PEEK\\[[^\\]]{1024}",
            "\\sEXAMINE\\s[^\\n]{100}",
            "\\sUNSUBSCRIBE\\s[^\\n]*?\\s\\{",
            "<9-15>国",
            "\\p{P}",
            "[我你他]",
            "灰白质,[0-9]{3}智力",
            "提问,传讯,责难",

            "我(.*)长叹一声(\\S{0,100}),疲乏",
            "\\S*,疲乏",

            "国家.*有没有未来",
            "国家",
            "国家(?=1973)",
            "^((?!that).)*this(.(?<!that))",
            "^[0-9]*",
            "^(a*)b",
            "打卡|[我俺][的の]?(老婆|嫁)|(金坷垃.{0,4})\\B是我的",
            "银[你她它他][马妈娘妹奶]|怎么.{0,4}不去",
            "周指活|没有.+要死了",
            "[你妮呢拟尼泥妳伱].*好$|[位个只].*好",
            "[生熟]肉|[画音]质|[只位个人].*你.*[好耗号壕]",
            "玩玩玩|[好不]玩|防不胜防|[触猝]不及防|充钱|充充充|就服你|冲[钱了]|我选择|氪.?$|我要[充冲].*[亿万百千元]",
            "(\\d{4})(-)(\\d{2})(-)(\\d{2})",
            "国家\\S*?好([^国]{0,100})我",
            "(a+){1,100}",
            "(a+){1,100}s"

    };

    static String input = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaab提1问,传讯, LOGIN a% 责难我还是10国家1973长叹一声啊,疲乏生肉你国家1972-01-22不好啊有没有未来好玩我要充一亿灰白质,123智力";

    @Test
    public void testBad() {
        PikeCompiler compiler = new PikeCompiler();
        String badRegex = "^([hH][tT]{2}[pP]://|[hH][tT]{2}[pP][sS]://)(([A-Za-z0-9-~]+).)+([A-Za-z0-9-~\\\\/])+$";
        String bugUrl = "http://www.fapiao.com/dddp-web/pdf/download?request=6e7JGxxxxx4ILd-kExxxxxxxqJ4-CHLmqVnenXC692m74H38sdfdsazxcUmfcOH2fAfY1Vw__%5EDadIfJgiEf";

        PikeVM vm = compiler.compileVM(badRegex, null);
        boolean r = vm.matches(bugUrl.toCharArray(), 0, bugUrl.length(), false, false, null, new PikeVM.Result() {
            @Override
            public void set(int[] start, int[] end) {

            }
        });
        System.out.println(r);
    }

    @Test
    public void testHook() {
        PikeCompiler compiler = new PikeCompiler();
        PikeVM vm = compiler.compileVM("^a\\h{0,0}c", null);
        String input = "abc";
        boolean matcher = vm.matches(input.toCharArray(), 0, input.length(), false, false,
                (int[] info) -> {
                    return 1;
                },
                (start, end) -> {

                });
        System.out.println(matcher);
        ;
    }

    @Test
    public void testSingle() {
        PikeCompiler compiler = new PikeCompiler();
        PikeVM vm = compiler.compileVM("^abc", null);
        PikeVM vm2 = compiler.compileVM("abc", null);
        PikeNFA nfa = new PikeNFA(vm);

        PikeNFA nfa2 = new PikeNFA(vm2);

        NFAHelper nfaHelper = new NFAHelper();
        nfaHelper.resetTo(nfa.getStateList(), 0);

        int dfaId = 0;
        nfaHelper.makeDfa(dfaId, 0);

        nfaHelper.resetTo(nfa2.getStateList(), 0);
        nfaHelper.makeDfa(dfaId, 0);

    }

    @Test
    public void testDate() throws IOException {
        String regexp = "(<190-205>)年";
        long start = System.currentTimeMillis();
        AutomatonBuilder builder = new AutomatonBuilder();
        Automaton automation = builder.build(10000, regexp);

        String data = "192年";

        AutomatonMatcher.MatcherCallback matcherCallback = new AutomatonMatcher.MatcherCallback() {

            @Override
            public void hitInfo(int id, int[] start, int[] end) {
                System.out.print(id + ": " + data.substring(start[0], end[0]));
                for (int i = 1; i < start.length; i++) {
                    if (start[i] != -1) {
                        System.out.print(" sub" + i + ":" + data.substring(start[i], end[i]));
                    }
                }
                System.out.println();
            }
        };
        AutomatonMatcher matcher = new AutomatonMatcher(automation, data, matcherCallback, null);

        System.out.println(matcher.find());

    }



    @Test
    public void testRegExp() throws IOException {
        CharacterParser.OPTIMIZE_RANGE = true;
        AutomatonBuilder builder = new AutomatonBuilder();

        long start = System.currentTimeMillis();
        Automaton automation = builder.build(10000, regexp);

        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - start) / 1000);

        System.out.println("count=" + automation.getDfaStateCount());
        if (automation.getDfaPendingStates() != null) {
            System.out.println("pending count=" + automation.getDfaPendingStates().length);

        } else {
            System.out.println("pending count=0");
        }

        //String input = "a提1问";
        AutomatonMatcher.MatcherCallback matcherCallback = new AutomatonMatcher.MatcherCallback() {
            @Override
            public boolean hit(int id) {
                return true;
            }

            @Override
            public void hitInfo(int id, int[] start, int[] end) {
                System.out.print(id + ": " + input.substring(start[0], end[0]));
                for (int i = 1; i < start.length; i++) {
                    if (start[i] != -1) {
                        System.out.print(" sub" + i + ":" + input.substring(start[i], end[i]));
                    }
                }
                System.out.println();
            }
        };

        AutomatonMatcher.HookCallback hookCallback = new AutomatonMatcher.HookCallback() {
            @Override
            public int hook(int type, int dfaId, int[] args) {
                if (type == 0) {
                    return dfaHook(args);
                } else {
                    return nfaHook(dfaId, args);
                }
            }

            /**
             *
             * @param hookArgs
             * @return
             *  0 continue hook
             *  1 hook succ and stop hook
             *  2 hook succ and continue hook
             *  otherwise stop hook
             */
            private int dfaHook(int[] hookArgs) {
                if (hookArgs[3] == hookArgs[2]) {
                    return 1;
                } else {
                    return -1;
                }
            }

            /**
             *
             * @param dfaId
             * @param hookArgs
             * @return
             *  0 continue hook
             *  1 hook succ and stop hook
             *  2 hook succ and continue hook
             *  otherwise stop hook
             */
            private int nfaHook(int dfaId, int[] hookArgs) {
                if (hookArgs[3] == hookArgs[2]) {
                    return 1;
                } else {
                    return -1;
                }
            }
        };

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);
        automation.save(out);
        out.flush();
        byte[] bytes = stream.toByteArray();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream input_data = new DataInputStream(inputStream);

        Automaton automaton2 = new Automaton();
        automaton2.load(input_data);

        AutomatonMatcher matcher = new AutomatonMatcher(automation, input, matcherCallback, hookCallback);
        matcher.find();

        matcher.switchTo(automaton2);
        matcher.find();

    }
}
