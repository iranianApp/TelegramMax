/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import com.maxgeram.amir.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class ContextLinkCell extends View implements MediaController.FileDownloadProgressListener {

    private final static int DOCUMENT_ATTACH_TYPE_NONE = 0;
    private final static int DOCUMENT_ATTACH_TYPE_DOCUMENT = 1;
    private final static int DOCUMENT_ATTACH_TYPE_GIF = 2;
    private final static int DOCUMENT_ATTACH_TYPE_AUDIO = 3;
    private final static int DOCUMENT_ATTACH_TYPE_VIDEO = 4;
    private final static int DOCUMENT_ATTACH_TYPE_MUSIC = 5;
    private final static int DOCUMENT_ATTACH_TYPE_STICKER = 6;
    private final static int DOCUMENT_ATTACH_TYPE_PHOTO = 7;
    private final static int DOCUMENT_ATTACH_TYPE_GEO = 8;

    public interface ContextLinkCellDelegate {
        void didPressedImage(ContextLinkCell cell);
    }

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;
    private LetterDrawable letterDrawable;

    private boolean needDivider;
    private boolean buttonPressed;
    private boolean needShadow;

    private int linkY;
    private StaticLayout linkLayout;

    private int titleY = AndroidUtilities.dp(7);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(27);
    private StaticLayout descriptionLayout;

    private TLRPC.BotInlineResult inlineResult;
    private TLRPC.Document documentAttach;
    private int documentAttachType;
    private boolean mediaWebpage;
    private MessageObject currentMessageObject;

    private static TextPaint titleTextPaint;
    private static TextPaint descriptionTextPaint;
    private static Paint paint;
    private static Drawable shadowDrawable;

    private int TAG;
    private int buttonState;
    private RadialProgress radialProgress;

    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private long time = 0;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);

    private ContextLinkCellDelegate delegate;

    public ContextLinkCell(Context context) {
        super(context);

        if (titleTextPaint == null) {
            titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            titleTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextPaint.setColor(0xff212121);

            descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        titleTextPaint.setTextSize(AndroidUtilities.dp(15));
        descriptionTextPaint.setTextSize(AndroidUtilities.dp(13));

        linkImageView = new ImageReceiver(this);
        letterDrawable = new LetterDrawable();
        radialProgress = new RadialProgress(this);
        TAG = MediaController.getInstance().generateObserverTag();
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        linkLayout = null;
        linkY = AndroidUtilities.dp(27);

        if (inlineResult == null && documentAttach == null) {
            setMeasuredDimension(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
            return;
        }

        int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = viewWidth - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8);

        TLRPC.PhotoSize currentPhotoObject = null;
        TLRPC.PhotoSize currentPhotoObjectThumb = null;
        ArrayList<TLRPC.PhotoSize> photoThumbs = null;
        String url = null;

        if (documentAttach != null) {
            photoThumbs = new ArrayList<>();
            photoThumbs.add(documentAttach.thumb);
        } else if (inlineResult != null && inlineResult.photo != null) {
            photoThumbs = new ArrayList<>(inlineResult.photo.sizes);
        }

        if (!mediaWebpage && inlineResult != null) {
            if (inlineResult.title != null) {
                try {
                    int width = (int) Math.ceil(titleTextPaint.measureText(inlineResult.title));
                    CharSequence titleFinal = TextUtils.ellipsize(Emoji.replaceEmoji(inlineResult.title.replace('\n', ' '), titleTextPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false), titleTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
                    titleLayout = new StaticLayout(titleFinal, titleTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                letterDrawable.setTitle(inlineResult.title);
            }

            if (inlineResult.description != null) {
                try {
                    descriptionLayout = ChatMessageCell.generateStaticLayout(Emoji.replaceEmoji(inlineResult.description, descriptionTextPaint.getFontMetricsInt(), AndroidUtilities.dp(13), false), descriptionTextPaint, maxWidth, maxWidth, 0, 3);
                    if (descriptionLayout.getLineCount() > 0) {
                        linkY = descriptionY + descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(1);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            if (inlineResult.url != null) {
                try {
                    int width = (int) Math.ceil(descriptionTextPaint.measureText(inlineResult.url));
                    CharSequence linkFinal = TextUtils.ellipsize(inlineResult.url.replace('\n', ' '), descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.MIDDLE);
                    linkLayout = new StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }

        String ext = null;
        if (documentAttach != null) {
            if (MessageObject.isGifDocument(documentAttach)) {
                currentPhotoObject = documentAttach.thumb;
            } else if (MessageObject.isStickerDocument(documentAttach)) {
                currentPhotoObject = documentAttach.thumb;
                ext = "webp";
            } else {
                if (documentAttachType != DOCUMENT_ATTACH_TYPE_MUSIC && documentAttachType != DOCUMENT_ATTACH_TYPE_AUDIO) {
                    currentPhotoObject = documentAttach.thumb;
                }
            }
        } else if (inlineResult != null && inlineResult.photo != null) {
            currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, AndroidUtilities.getPhotoSize(), true);
            currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
        }
        if (inlineResult != null) {
            if (inlineResult.content_url != null) {
                if (inlineResult.type != null) {
                    if (inlineResult.type.startsWith("gif")) {
                        if (documentAttachType != DOCUMENT_ATTACH_TYPE_GIF) {
                            url = inlineResult.content_url;
                            documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
                        }
                    } else if (inlineResult.type.equals("photo")) {
                        url = inlineResult.thumb_url;
                        if (url == null) {
                            url = inlineResult.content_url;
                        }
                    }
                }
            }
            if (url == null && inlineResult.thumb_url != null) {
                url = inlineResult.thumb_url;
            }
        }
        if (url == null && currentPhotoObject == null && currentPhotoObjectThumb == null) {
            if (inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue || inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo) {
                double lat = inlineResult.send_message.geo.lat;
                double lon = inlineResult.send_message.geo._long;
                url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=15&size=72x72&maptype=roadmap&scale=%d&markers=color:red|size:small|%f,%f&sensor=false", lat, lon, Math.min(2, (int) Math.ceil(AndroidUtilities.density)), lat, lon);
            }
        }

        int width;
        int w = 0;
        int h = 0;

        if (documentAttach != null) {
            for (int b = 0; b < documentAttach.attributes.size(); b++) {
                TLRPC.DocumentAttribute attribute = documentAttach.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    w = attribute.w;
                    h = attribute.h;
                    break;
                }
            }
        }
        if (w == 0 || h == 0) {
            if (currentPhotoObject != null) {
                if (currentPhotoObjectThumb != null) {
                    currentPhotoObjectThumb.size = -1;
                }
                w = currentPhotoObject.w;
                h = currentPhotoObject.h;
            } else if (inlineResult != null) {
                w = inlineResult.w;
                h = inlineResult.h;
            }
        }
        if (w == 0 || h == 0) {
            w = h = AndroidUtilities.dp(80);
        }
        if (documentAttach != null || currentPhotoObject != null || url != null) {
            String currentPhotoFilter;
            String currentPhotoFilterThumb = "52_52_b";

            if (mediaWebpage) {
                width = (int) (w / (h / (float) AndroidUtilities.dp(80)));
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                    currentPhotoFilterThumb = currentPhotoFilter = String.format(Locale.US, "%d_%d_b", (int) (width / AndroidUtilities.density), 80);
                } else {
                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (width / AndroidUtilities.density), 80);
                    currentPhotoFilterThumb = currentPhotoFilter + "_b";
                }
            } else {
                currentPhotoFilter = "52_52";
            }
            linkImageView.setAspectFit(documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER);

            if (documentAttachType == DOCUMENT_ATTACH_TYPE_GIF) {
                if (documentAttach != null) {
                    linkImageView.setImage(documentAttach, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, documentAttach.size, ext, false);
                } else {
                    linkImageView.setImage(null, url, null, null, currentPhotoObject != null ? currentPhotoObject.location : null, currentPhotoFilter, -1, ext, true);
                }
            } else {
                if (currentPhotoObject != null) {
                    linkImageView.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, currentPhotoObject.size, ext, false);
                } else {
                    linkImageView.setImage(null, url, currentPhotoFilter, null, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, -1, ext, true);
                }
            }
            drawLinkImageView = true;
        }

        if (mediaWebpage) {
            setBackgroundDrawable(null);
            width = viewWidth;
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (height == 0) {
                height = AndroidUtilities.dp(100);
            }
            setMeasuredDimension(width, height);
            int x = (width - AndroidUtilities.dp(24)) / 2;
            int y = (height - AndroidUtilities.dp(24)) / 2;
            radialProgress.setProgressRect(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
            linkImageView.setImageCoords(0, 0, width, height);
        } else {
            setBackgroundResource(R.drawable.list_selector);
            int height = 0;
            if (titleLayout != null && titleLayout.getLineCount() != 0) {
                height += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
            }
            if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
                height += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
            }
            if (linkLayout != null && linkLayout.getLineCount() > 0) {
                height += linkLayout.getLineBottom(linkLayout.getLineCount() - 1);
            }
            height = Math.max(AndroidUtilities.dp(52), height);
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(68), height + AndroidUtilities.dp(16)) + (needDivider ? 1 : 0));

            int maxPhotoWidth = AndroidUtilities.dp(52);
            int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(8) - maxPhotoWidth : AndroidUtilities.dp(8);
            letterDrawable.setBounds(x, AndroidUtilities.dp(8), x + maxPhotoWidth, AndroidUtilities.dp(60));
            linkImageView.setImageCoords(x, AndroidUtilities.dp(8), maxPhotoWidth, maxPhotoWidth);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgressRect(x + AndroidUtilities.dp(4), AndroidUtilities.dp(12), x + AndroidUtilities.dp(48), AndroidUtilities.dp(56));
            }
        }
    }

    private void setAttachType() {
        currentMessageObject = null;
        documentAttachType = DOCUMENT_ATTACH_TYPE_NONE;
        if (documentAttach != null) {
            if (MessageObject.isGifDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_GIF;
            } else if (MessageObject.isStickerDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_STICKER;
            } else if (MessageObject.isMusicDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
            } else if (MessageObject.isVoiceDocument(documentAttach)) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO;
            }
        } else if (inlineResult != null) {
            if (inlineResult.photo != null) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_PHOTO;
            } else if (inlineResult.type.equals("audio")) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_MUSIC;
            } else if (inlineResult.type.equals("voice")) {
                documentAttachType = DOCUMENT_ATTACH_TYPE_AUDIO;
            }
        }
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.out = true;
            message.id = -Utilities.random.nextInt();
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = message.from_id = UserConfig.getClientUserId();
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.message = "-1";
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.document = new TLRPC.TL_document();
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

            if (documentAttach != null) {
                message.media.document = documentAttach;
                message.attachPath = "";
            } else {
                String ext = ImageLoader.getHttpUrlExtension(inlineResult.content_url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                message.media.document.id = 0;
                message.media.document.access_hash = 0;
                message.media.document.date = message.date;
                message.media.document.mime_type = "audio/" + ext;
                message.media.document.size = 0;
                message.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
                message.media.document.thumb.type = "s";
                message.media.document.dc_id = 0;

                TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                attributeAudio.duration = inlineResult.duration;
                attributeAudio.title = inlineResult.title != null ? inlineResult.title : "";
                attributeAudio.performer = inlineResult.description != null ? inlineResult.description : "";
                attributeAudio.flags |= 3;
                if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                    attributeAudio.voice = true;
                }
                message.media.document.attributes.add(attributeAudio);

                TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                fileName.file_name = Utilities.MD5(inlineResult.content_url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content_url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                message.media.document.attributes.add(fileName);

                message.attachPath = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(inlineResult.content_url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content_url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg")).getAbsolutePath();
            }

            currentMessageObject = new MessageObject(message, null, false);
        }
    }

    public void setLink(TLRPC.BotInlineResult contextResult, boolean media, boolean divider, boolean shadow) {
        needDivider = divider;
        needShadow = shadow;
        if (needShadow && shadowDrawable == null) {
            shadowDrawable = getContext().getResources().getDrawable(R.drawable.header_shadow);
        }
        inlineResult = contextResult;
        if (inlineResult != null && inlineResult.document != null) {
            documentAttach = inlineResult.document;
        } else {
            documentAttach = null;
        }
        mediaWebpage = media;
        setAttachType();
        requestLayout();
        updateButtonState(false);
    }

    public void setGif(TLRPC.Document document, boolean divider) {
        needDivider = divider;
        needShadow = false;
        inlineResult = null;
        documentAttach = document;
        mediaWebpage = true;
        setAttachType();
        requestLayout();
        updateButtonState(false);
    }

    public boolean isSticker() {
        return documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER;
    }

    public boolean showingBitmap() {
        return linkImageView.getBitmap() != null;
    }

    public TLRPC.Document getDocument() {
        return documentAttach;
    }

    public ImageReceiver getPhotoImage() {
        return linkImageView;
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (drawLinkImageView) {
            linkImageView.onDetachedFromWindow();
        }
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawLinkImageView) {
            if (linkImageView.onAttachedToWindow()) {
                updateButtonState(false);
            }
        }
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(event.getX(), event.getY());
            }
        }

        if (mediaWebpage || delegate == null || inlineResult == null) {
            return super.onTouchEvent(event);
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(48);
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            boolean area = letterDrawable.getBounds().contains(x, y);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (area) {
                    buttonPressed = true;
                    invalidate();
                    result = true;
                    radialProgress.swapBackground(getDrawableForCurrentState());
                }
            } else if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = false;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!area) {
                        buttonPressed = false;
                        invalidate();
                    }
                }
                radialProgress.swapBackground(getDrawableForCurrentState());
            }
        } else {
            if (inlineResult != null && inlineResult.content_url != null && inlineResult.content_url.length() > 0) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (letterDrawable.getBounds().contains(x, y)) {
                        buttonPressed = true;
                        result = true;
                    }
                } else {
                    if (buttonPressed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            buttonPressed = false;
                            playSoundEffect(SoundEffectConstants.CLICK);
                            delegate.didPressedImage(this);
                        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                            buttonPressed = false;
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            if (!letterDrawable.getBounds().contains(x, y)) {
                                buttonPressed = false;
                            }
                        }
                    }
                }
            }
        }
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    private void didPressedButton() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == 0) {
                if (MediaController.getInstance().playAudio(currentMessageObject)) {
                    buttonState = 1;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 1) {
                boolean result = MediaController.getInstance().pauseAudio(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 2) {
                radialProgress.setProgress(0, false);
                if (documentAttach != null) {
                    FileLoader.getInstance().loadFile(documentAttach, true, false);
                } else {
                    ImageLoader.getInstance().loadHttpFile(inlineResult.content_url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg");
                }
                buttonState = 4;
                radialProgress.setBackground(getDrawableForCurrentState(), true, false);
                invalidate();
            } else if (buttonState == 4) {
                if (documentAttach != null) {
                    FileLoader.getInstance().cancelLoadFile(documentAttach);
                } else {
                    ImageLoader.getInstance().cancelLoadHttpFile(inlineResult.content_url);
                }
                buttonState = 2;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout != null) {
            descriptionTextPaint.setColor(0xff8a8a8a);
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            descriptionLayout.draw(canvas);
            canvas.restore();
        }

        if (linkLayout != null) {
            descriptionTextPaint.setColor(Theme.MSG_LINK_TEXT_COLOR);
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), linkY);
            linkLayout.draw(canvas);
            canvas.restore();
        }

        if (!mediaWebpage) {
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
                radialProgress.setProgressColor(buttonPressed ? Theme.MSG_IN_AUDIO_SELECTED_PROGRESS_COLOR : Theme.MSG_IN_AUDIO_PROGRESS_COLOR);
                radialProgress.draw(canvas);
            } else if (inlineResult != null && inlineResult.type.equals("file")) {
                int w = Theme.inlineDocDrawable.getIntrinsicWidth();
                int h = Theme.inlineDocDrawable.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.inlineDocDrawable.setBounds(x, y, x + w, y + h);
                Theme.inlineDocDrawable.draw(canvas);
            } else if (inlineResult != null && (inlineResult.type.equals("audio") || inlineResult.type.equals("voice"))) {
                int w = Theme.inlineAudioDrawable.getIntrinsicWidth();
                int h = Theme.inlineAudioDrawable.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.inlineAudioDrawable.setBounds(x, y, x + w, y + h);
                Theme.inlineAudioDrawable.draw(canvas);
            } else if (inlineResult != null && (inlineResult.type.equals("venue") || inlineResult.type.equals("geo"))) {
                int w = Theme.inlineLocationDrawable.getIntrinsicWidth();
                int h = Theme.inlineLocationDrawable.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (AndroidUtilities.dp(52) - w) / 2;
                int y = linkImageView.getImageY() + (AndroidUtilities.dp(52) - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + AndroidUtilities.dp(52), linkImageView.getImageY() + AndroidUtilities.dp(52), LetterDrawable.paint);
                Theme.inlineLocationDrawable.setBounds(x, y, x + w, y + h);
                Theme.inlineLocationDrawable.draw(canvas);
            } else {
                letterDrawable.draw(canvas);
            }
        } else {
            if (inlineResult != null && (inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaGeo || inlineResult.send_message instanceof TLRPC.TL_botInlineMessageMediaVenue)) {
                int w = Theme.inlineLocationDrawable.getIntrinsicWidth();
                int h = Theme.inlineLocationDrawable.getIntrinsicHeight();
                int x = linkImageView.getImageX() + (linkImageView.getImageWidth() - w) / 2;
                int y = linkImageView.getImageY() + (linkImageView.getImageHeight() - h) / 2;
                canvas.drawRect(linkImageView.getImageX(), linkImageView.getImageY(), linkImageView.getImageX() + linkImageView.getImageWidth(), linkImageView.getImageY() + linkImageView.getImageHeight(), LetterDrawable.paint);
                Theme.inlineLocationDrawable.setBounds(x, y, x + w, y + h);
                Theme.inlineLocationDrawable.draw(canvas);
            }
        }
        if (drawLinkImageView) {
            canvas.save();
            if (scaled && scale != 0.8f || !scaled && scale != 1.0f) {
                long newTime = System.currentTimeMillis();
                long dt = (newTime - lastUpdateTime);
                lastUpdateTime = newTime;
                if (scaled && scale != 0.8f) {
                    scale -= dt / 400.0f;
                    if (scale < 0.8f) {
                        scale = 0.8f;
                    }
                } else {
                    scale += dt / 400.0f;
                    if (scale > 1.0f) {
                        scale = 1.0f;
                    }
                }
                invalidate();
            }
            canvas.scale(scale, scale, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
            linkImageView.draw(canvas);
            canvas.restore();
        }
        if (mediaWebpage && (documentAttachType == DOCUMENT_ATTACH_TYPE_PHOTO || documentAttachType == DOCUMENT_ATTACH_TYPE_GIF)) {
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.draw(canvas);
        }

        if (needDivider && !mediaWebpage) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, paint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, paint);
            }
        }
        if (needShadow && shadowDrawable != null) {
            shadowDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(3));
            shadowDrawable.draw(canvas);
        }
    }

    private Drawable getDrawableForCurrentState() {
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState == -1) {
                return null;
            }
            radialProgress.setAlphaForPrevious(false);
            return Theme.fileStatesDrawable[buttonState + 5][buttonPressed ? 1 : 0];
        }
        return buttonState == 1 ? Theme.photoStatesDrawables[5][0] : null;
    }

    public void updateButtonState(boolean animated) {
        String fileName = null;
        File cacheFile = null;
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
            if (documentAttach != null) {
                fileName = FileLoader.getAttachFileName(documentAttach);
                cacheFile = FileLoader.getPathToAttach(documentAttach);
            } else {
                fileName = inlineResult.content_url;
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(inlineResult.content_url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content_url, documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC ? "mp3" : "ogg"));
            }
        } else if (mediaWebpage) {
            if (inlineResult != null) {
                if (inlineResult.document instanceof TLRPC.TL_document) {
                    fileName = FileLoader.getAttachFileName(inlineResult.document);
                    cacheFile = FileLoader.getPathToAttach(inlineResult.document);
                } else if (inlineResult.photo instanceof TLRPC.TL_photo) {
                    TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(inlineResult.photo.sizes, AndroidUtilities.getPhotoSize(), true);
                    fileName = FileLoader.getAttachFileName(currentPhotoObject);
                    cacheFile = FileLoader.getPathToAttach(currentPhotoObject);
                } else if (inlineResult.content_url != null) {
                    fileName = Utilities.MD5(inlineResult.content_url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.content_url, "jpg");
                    cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                } else if (inlineResult.thumb_url != null) {
                    fileName = Utilities.MD5(inlineResult.thumb_url) + "." + ImageLoader.getHttpUrlExtension(inlineResult.thumb_url, "jpg");
                    cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                }
            } else if (documentAttach != null) {
                fileName = FileLoader.getAttachFileName(documentAttach);
                cacheFile = FileLoader.getPathToAttach(documentAttach);
            }
        }
        if (TextUtils.isEmpty(fileName)) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        if (cacheFile.exists() && cacheFile.length() == 0) {
            cacheFile.delete();
        }
        if (!cacheFile.exists()) {
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                boolean isLoading;
                if (documentAttach != null) {
                    isLoading = FileLoader.getInstance().isLoadingFile(fileName);
                } else {
                    isLoading = ImageLoader.getInstance().isLoadingHttpFile(fileName);
                }
                if (!isLoading) {
                    buttonState = 2;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                } else {
                    buttonState = 4;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                }
            } else {
                buttonState = 1;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                float setProgress = progress != null ? progress : 0;
                radialProgress.setProgress(setProgress, false);
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            }
            invalidate();
        } else {
            MediaController.getInstance().removeLoadingFileObserver(this);
            if (documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC || documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO) {
                boolean playing = MediaController.getInstance().isPlayingAudio(currentMessageObject);
                if (!playing || playing && MediaController.getInstance().isAudioPaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
            } else {
                buttonState = -1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        }
    }

    public void setDelegate(ContextLinkCellDelegate contextLinkCellDelegate) {
        delegate = contextLinkCellDelegate;
    }

    public TLRPC.BotInlineResult getResult() {
        return inlineResult;
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        updateButtonState(true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (documentAttachType == DOCUMENT_ATTACH_TYPE_AUDIO || documentAttachType == DOCUMENT_ATTACH_TYPE_MUSIC) {
            if (buttonState != 4) {
                updateButtonState(false);
            }
        } else {
            if (buttonState != 1) {
                updateButtonState(false);
            }
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
