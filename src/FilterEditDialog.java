import lrscp.lib.swt.SwtUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class FilterEditDialog extends Dialog {

    protected Object result;
    protected Shell shell;
    private Text text;
    private Text textTag;

    private FilterConfig mFilter;

    /**
     * Create the dialog.
     * 
     * @param parent
     * @param style
     */
    public FilterEditDialog(Shell parent, int style) {
        super(parent, SWT.DIALOG_TRIM);
        setText("SWT Dialog");
    }

    /**
     * Open the dialog.
     * 
     * @return the result
     */
    public Object open() {
        createContents();
        shell.open();
        shell.layout();
        Display display = getParent().getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }

    /**
     * Create contents of the dialog.
     */
    private void createContents() {
        shell = new Shell(getParent(), getStyle());
        shell.setSize(271, 212);
        shell.setText("过滤器");
        shell.setLayout(null);

        Label lblTag = new Label(shell, SWT.NONE);
        lblTag.setBounds(23, 33, 38, 20);
        lblTag.setText("名称");

        text = new Text(shell, SWT.BORDER);
        text.setBounds(65, 30, 172, 26);
        if (mFilter != null) {
            text.setText(mFilter.name);
        }

        Button button = new Button(shell, SWT.NONE);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onClickOk();
            }
        });
        button.setBounds(23, 140, 92, 30);
        button.setText("确定");

        Button button_1 = new Button(shell, SWT.NONE);
        button_1.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.dispose();
            }
        });
        button_1.setBounds(145, 140, 92, 30);
        button_1.setText("取消");

        SwtUtils.center(shell, 10);

        Label label = new Label(shell, SWT.NONE);
        label.setText("TAG");
        label.setBounds(23, 82, 38, 20);

        textTag = new Text(shell, SWT.BORDER);
        textTag.setBounds(65, 79, 172, 26);
        if (mFilter != null) {
            textTag.setText(mFilter.tag);
        }
    }

    protected void onClickOk() {
        if (text.getText().isEmpty() || textTag.getText().isEmpty()) {
            return;
        }
        String[] r = new String[2];
        r[0] = text.getText();
        r[1] = textTag.getText();
        result = r;
        shell.dispose();
    }

    public void setFilterConfig(FilterConfig f) {
        mFilter = f;
    }
}
