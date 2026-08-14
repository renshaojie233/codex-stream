package com.limelight.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.SystemClock;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    public StreamView(Context context) {
        super(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                EditorInfo.IME_FLAG_NO_FULLSCREEN;

        return new BaseInputConnection(this, true) {
            private long ignoreCleanupDeleteUntil;
            private int ignoreCleanupDeleteLength;

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (inputCallbacks != null && text != null && text.length() > 0) {
                    inputCallbacks.handleCommittedText(text);
                    ignoreCleanupDeleteLength = text.length();
                    ignoreCleanupDeleteUntil = SystemClock.uptimeMillis() + 180;
                }
                // This view is a stateless conduit to the remote host. Calling
                // BaseInputConnection here mutates a local editable and some
                // Xiaomi IMEs immediately follow that mutation with a cleanup
                // delete, which was being forwarded as a remote Backspace.
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                // Do not mirror transient Pinyin/IME composition to the host.
                // The finalized text arrives through commitText().
                return true;
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                return true;
            }

            @Override
            public boolean finishComposingText() {
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (SystemClock.uptimeMillis() <= ignoreCleanupDeleteUntil &&
                        beforeLength > 0 && beforeLength <= ignoreCleanupDeleteLength) {
                    // Some IMEs delete their local composition buffer directly
                    // after commitText(). It is not a user Backspace.
                    ignoreCleanupDeleteUntil = 0;
                    ignoreCleanupDeleteLength = 0;
                    return true;
                }
                if (inputCallbacks != null && beforeLength > 0) {
                    inputCallbacks.handleBackspace(beforeLength);
                }
                return true;
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                if (inputCallbacks != null) {
                    inputCallbacks.handleEnter();
                    return true;
                }
                return super.performEditorAction(editorAction);
            }
        };
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
        void handleCommittedText(CharSequence text);
        void handleBackspace(int count);
        void handleEnter();
    }
}
