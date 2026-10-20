package infinode.org.example.bFFA01.ui;

public final class ClockText {

    private ClockText() {
    }

    public static String mmss(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int minutes = seconds / 60;
        int remain = seconds % 60;
        return minutes + ":" + (remain < 10 ? "0" : "") + remain;
    }
}
