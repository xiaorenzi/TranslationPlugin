package cn.yiiguxing.plugin.translate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class TranslationBalloon implements TranslationView {

    private static final Icon ICON_PIN = IconLoader.getIcon("/pin.png");

    private static final int MIN_BALLOON_WIDTH = JBUI.scale(150);
    private static final int MIN_BALLOON_HEIGHT = JBUI.scale(50);
    private static final int MAX_BALLOON_SIZE = JBUI.scale(600);
    private static final JBInsets BORDER_INSETS = JBUI.insets(20, 20, 20, 20);

    private final JBPanel contentPanel;
    private final GroupLayout layout;
    private final JBLabel label;

    private Balloon myBalloon;

    private final TranslationPresenter mTranslationPresenter;

    private final Editor editor;

    public TranslationBalloon(@NotNull Editor editor) {
        this.editor = Objects.requireNonNull(editor, "editor cannot be null");

        contentPanel = new JBPanel<>();
        layout = new GroupLayout(contentPanel);
        contentPanel.setLayout(layout);

        label = new JBLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setText("Querying...");

        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(label, MIN_BALLOON_WIDTH, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(label, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));

        contentPanel.add(label);

        mTranslationPresenter = new TranslationPresenter(this);
    }

    @NotNull
    private BalloonBuilder buildBalloon() {
        return JBPopupFactory.getInstance()
                .createDialogBalloonBuilder(contentPanel, null)
                .setHideOnClickOutside(true)
                .setShadow(true)
                .setBlockClicksThroughBalloon(true)
                .setRequestFocus(true)
                .setBorderInsets(BORDER_INSETS);
    }

    public void showAndQuery(@NotNull String queryText) {
        myBalloon = buildBalloon().setCloseButtonEnabled(false).createBalloon();
        myBalloon.show(JBPopupFactory.getInstance().guessBestPopupLocation(editor), Balloon.Position.below);
        mTranslationPresenter.query(Objects.requireNonNull(queryText, "queryText cannot be null"));
    }

    @Override
    public void updateHistory() {
        // do nothing
    }

    @Override
    public void showResult(@NotNull String query, @NotNull QueryResult result) {
        if (this.myBalloon != null) {
            if (this.myBalloon.isDisposed()) {
                return;
            }

            this.myBalloon.hide(true);
        }

        contentPanel.remove(label);

        JTextPane resultText = new JTextPane();
        resultText.setEditable(false);
        resultText.setBackground(UIManager.getColor("Panel.background"));
        resultText.setFont(JBUI.Fonts.create("Microsoft YaHei", JBUI.scaleFontSize(14)));

        Utils.insertQueryResultText(resultText.getDocument(), result);
        resultText.setCaretPosition(0);

        JBScrollPane scrollPane = new JBScrollPane(resultText);
        scrollPane.setBorder(new JBEmptyBorder(0));
        scrollPane.setVerticalScrollBar(scrollPane.createVerticalScrollBar());
        scrollPane.setHorizontalScrollBar(scrollPane.createHorizontalScrollBar());

        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(scrollPane, MIN_BALLOON_WIDTH, GroupLayout.DEFAULT_SIZE, MAX_BALLOON_SIZE));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(scrollPane, MIN_BALLOON_HEIGHT, GroupLayout.DEFAULT_SIZE, MAX_BALLOON_SIZE));
        contentPanel.add(scrollPane);

        final BalloonImpl balloon = (BalloonImpl) buildBalloon().createBalloon();
        RelativePoint showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
        createPinButton(balloon, showPoint);
        balloon.show(showPoint, Balloon.Position.below);

        // 再刷新一下，尽可能地消除滚动条
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                balloon.revalidate();
            }
        });
    }

    private void createPinButton(final BalloonImpl balloon, final RelativePoint showPoint) {
        balloon.setActionProvider(new BalloonImpl.ActionProvider() {
            private BalloonImpl.ActionButton myPinButton;

            @NotNull
            public List<BalloonImpl.ActionButton> createActions() {
                myPinButton = balloon.new ActionButton(ICON_PIN, ICON_PIN, null,
                        new Consumer<MouseEvent>() {
                            @Override
                            public void consume(MouseEvent mouseEvent) {
                                if (mouseEvent.getClickCount() == 1) {
                                    balloon.hide(true);
                                    new TranslationDialog().show(editor.getProject(), null);
                                }
                            }
                        });

                return Collections.singletonList(myPinButton);
            }

            public void layout(@NotNull Rectangle lpBounds) {
                if (myPinButton.isVisible()) {
                    int iconWidth = ICON_PIN.getIconWidth();
                    int iconHeight = ICON_PIN.getIconHeight();
                    int margin = JBUI.scale(3);
                    int x = lpBounds.x + lpBounds.width - iconWidth - margin;
                    int y = lpBounds.y + margin;

                    Rectangle rectangle = new Rectangle(x, y, iconWidth, iconHeight);
                    Insets border = balloon.getShadowBorderInsets();
                    rectangle.x -= border.left;

                    int showX = showPoint.getPoint().x;
                    int showY = showPoint.getPoint().y;
                    int offset = JBUI.scale(1);// 误差
                    if (showX <= lpBounds.x + offset // 右方
                            || showX >= (lpBounds.x + lpBounds.width - offset) // 左方
                            || (lpBounds.y >= showY // 下方
                            || (lpBounds.y + lpBounds.height) <= showY/*上方*/)) {
                        rectangle.y += border.top;
                    }

                    myPinButton.setBounds(rectangle);
                }
            }
        });
    }

    @Override
    public void showError(@NotNull String error) {
        if (myBalloon == null)
            return;

        label.setForeground(new JBColor(new Color(0xFF333333), new Color(0xFFFF2222)));
        label.setText(error);
        myBalloon.revalidate();
    }
}
