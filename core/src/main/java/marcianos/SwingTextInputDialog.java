package marcianos;

import java.io.File;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/** Swing text-input dialog with a stable title and optional app icon. */
public final class SwingTextInputDialog {
    private static final String DIALOG_TITLE = "Fill in";
    private static final Icon APP_ICON = loadAppIcon();

    private SwingTextInputDialog() {
    }

    public static String show(String prompt, String initialValue) {
        Object value = JOptionPane.showInputDialog(
            null,
            prompt,
            DIALOG_TITLE,
            JOptionPane.QUESTION_MESSAGE,
            APP_ICON,
            null,
            initialValue);
        return value == null ? null : value.toString();
    }

    private static Icon loadAppIcon() {
        URL resource = SwingTextInputDialog.class.getClassLoader().getResource("cutre-marcianos.ico");
        if (resource != null) return new ImageIcon(resource);
        File localIcon = new File("resources", "cutre-marcianos.ico");
        if (localIcon.isFile()) return new ImageIcon(localIcon.getAbsolutePath());
        return null;
    }
}