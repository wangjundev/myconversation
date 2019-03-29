/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui.conversation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.text.util.LinkifyCompat;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Resources;

import com.alipay.sdk.app.H5PayCallback;
import com.alipay.sdk.app.PayTask;
import com.alipay.sdk.util.H5PayResultModel;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.datamodel.media.ImageRequestDescriptor;
import com.android.messaging.datamodel.media.MessagePartImageRequestDescriptor;
import com.android.messaging.datamodel.media.UriImageRequestDescriptor;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.ui.AsyncImageView;
import com.android.messaging.ui.AsyncImageView.AsyncImageViewDelayLoader;
import com.android.messaging.ui.AudioAttachmentView;
import com.android.messaging.ui.ContactIconView;
import com.android.messaging.ui.ConversationDrawables;
import com.android.messaging.ui.MultiAttachmentLayout;
import com.android.messaging.ui.MultiAttachmentLayout.OnAttachmentClickListener;
import com.android.messaging.ui.PersonItemView;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.VideoThumbnailView;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.YouTubeUtil;
import com.baidu.mapapi.map.MapView;
import com.google.common.base.Predicate;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * The view for a single entry in a conversation.
 */
public class ConversationMessageView extends FrameLayout implements View.OnClickListener,
        View.OnLongClickListener, OnAttachmentClickListener {
    public interface ConversationMessageViewHost {
        boolean onAttachmentClick(ConversationMessageView view, MessagePartData attachment,
                                  Rect imageBounds, boolean longPress);
        SubscriptionListEntry getSubscriptionEntryForSelfParticipant(String selfParticipantId,
                                                                     boolean excludeDefault);
    }

    private final ConversationMessageData mData;

    private LinearLayout mMessageAttachmentsView;
    private MultiAttachmentLayout mMultiAttachmentView;
    private AsyncImageView mMessageImageView;
    private TextView mMessageTextView;
    //add by junwang start
    private static final long MAX_INTERVAL_TIME = 3*24*3600;
    private WebView mMessageWebView;
    private boolean mHasWebLinks;
    private int mWebViewWidth;
    private MapView mMapView;
    private Button mLocationButton;
    private Button mVivoPayButton;
    private long mIntervalTime;
    private String mUrl;
    private LoadUrl mLoadUrl;
    private int mScreenWidth;
    private int mScreenHeight;
    //add by junwang end
    private boolean mMessageTextHasLinks;
    private boolean mMessageHasYouTubeLink;
    private TextView mStatusTextView;
    private TextView mTitleTextView;
    private TextView mMmsInfoTextView;
    private LinearLayout mMessageTitleLayout;
    private TextView mSenderNameTextView;
    private ContactIconView mContactIconView;
    private ConversationMessageBubbleView mMessageBubble;
    private View mSubjectView;
    private TextView mSubjectLabel;
    private TextView mSubjectText;
    private View mDeliveredBadge;
    private ViewGroup mMessageMetadataView;
    private ViewGroup mMessageTextAndInfoView;
    private TextView mSimNameView;

    private boolean mOneOnOne;
    private ConversationMessageViewHost mHost;

    //add by junwang for action type
    public static final int OPEN_LOCATION = 0;// 打开定位
    public static final int CALL_PHONE = 1;// 拨打电话
    //add by junwang
    private boolean mIsContactInWhiteList;

    public ConversationMessageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // TODO: we should switch to using Binding and DataModel factory methods.
        mData = new ConversationMessageData();
    }

    @Override
    protected void onFinishInflate() {
        mContactIconView = (ContactIconView) findViewById(R.id.conversation_icon);
        mContactIconView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                ConversationMessageView.this.performLongClick();
                return true;
            }
        });

        mMessageAttachmentsView = (LinearLayout) findViewById(R.id.message_attachments);
        mMultiAttachmentView = (MultiAttachmentLayout) findViewById(R.id.multiple_attachments);
        mMultiAttachmentView.setOnAttachmentClickListener(this);

        mMessageImageView = (AsyncImageView) findViewById(R.id.message_image);
        mMessageImageView.setOnClickListener(this);
        mMessageImageView.setOnLongClickListener(this);

        mMessageTextView = (TextView) findViewById(R.id.message_text);
        mMessageTextView.setOnClickListener(this);
        IgnoreLinkLongClickHelper.ignoreLinkLongClick(mMessageTextView, this);

        //add by junwang
        mMessageWebView =(WebView)findViewById(R.id.message_webview);
        //mIsContactInWhiteList = ConversationFragment.isContactInWebViewWhiteList(mMessageWebView.getContext());
        mMapView = (MapView)findViewById(R.id.map_view);
        mLocationButton = (Button)findViewById(R.id.location_button);
        mLocationButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mMessageWebView.getContext().startActivity(new Intent(mMessageWebView.getContext(), BaiduMapTestActivity.class));
            }
        });
        //mLoadUrl = new LoadUrl();
        WindowManager manager = (WindowManager) getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
        mScreenHeight = outMetrics.heightPixels;
        mVivoPayButton = (Button)findViewById(R.id.vivopay_button);
        /*mVivoPayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //mVivoPayButton.getContext().startActivity(new Intent(mMessageWebView.getContext(), PayActivity.class));
                mVivoPayButton.getContext().startActivity(new Intent(mMessageWebView.getContext(), MiPayMainActivity.class));
//               mMessageWebView.loadUrl("file:///android_res/raw/test3.html");
//                mMessageWebView.loadUrl("file:///android_res/raw/test1.html");
//                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wxpay.wxutil.com/mch/pay/h5.v2.php"));
//                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                mMessageWebView.getContext().startActivity(intent);
            }
        });*/

        mStatusTextView = (TextView) findViewById(R.id.message_status);
        mTitleTextView = (TextView) findViewById(R.id.message_title);
        mMmsInfoTextView = (TextView) findViewById(R.id.mms_info);
        mMessageTitleLayout = (LinearLayout) findViewById(R.id.message_title_layout);
        mSenderNameTextView = (TextView) findViewById(R.id.message_sender_name);
        mMessageBubble = (ConversationMessageBubbleView) findViewById(R.id.message_content);
        mSubjectView = findViewById(R.id.subject_container);
        mSubjectLabel = (TextView) mSubjectView.findViewById(R.id.subject_label);
        mSubjectText = (TextView) mSubjectView.findViewById(R.id.subject_text);
        mDeliveredBadge = findViewById(R.id.smsDeliveredBadge);
        mMessageMetadataView = (ViewGroup) findViewById(R.id.message_metadata);
        mMessageTextAndInfoView = (ViewGroup) findViewById(R.id.message_text_and_info);
        mSimNameView = (TextView) findViewById(R.id.sim_name);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int horizontalSpace = MeasureSpec.getSize(widthMeasureSpec);
        final int iconSize = getResources()
                .getDimensionPixelSize(R.dimen.conversation_message_contact_icon_size);

        final int unspecifiedMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int iconMeasureSpec = MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY);

        mContactIconView.measure(iconMeasureSpec, iconMeasureSpec);

        final int arrowWidth =
                getResources().getDimensionPixelSize(R.dimen.message_bubble_arrow_width);

        // We need to subtract contact icon width twice from the horizontal space to get
        // the max leftover space because we want the message bubble to extend no further than the
        // starting position of the message bubble in the opposite direction.
        final int maxLeftoverSpace = horizontalSpace - mContactIconView.getMeasuredWidth() * 2
                - arrowWidth - getPaddingLeft() - getPaddingRight();
        final int messageContentWidthMeasureSpec = MeasureSpec.makeMeasureSpec(maxLeftoverSpace,
                MeasureSpec.AT_MOST);

        mMessageBubble.measure(messageContentWidthMeasureSpec, unspecifiedMeasureSpec);

        final int maxHeight = Math.max(mContactIconView.getMeasuredHeight(),
                mMessageBubble.getMeasuredHeight());
        setMeasuredDimension(horizontalSpace, maxHeight + getPaddingBottom() + getPaddingTop());
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right,
                            final int bottom) {
        final boolean isRtl = AccessibilityUtil.isLayoutRtl(this);

        final int iconWidth = mContactIconView.getMeasuredWidth();
        final int iconHeight = mContactIconView.getMeasuredHeight();
        final int iconTop = getPaddingTop();
        final int contentWidth = (right -left) - iconWidth - getPaddingLeft() - getPaddingRight();
        final int contentHeight = mMessageBubble.getMeasuredHeight();
        final int contentTop = iconTop;

        final int iconLeft;
        final int contentLeft;
        if (mData.getIsIncoming()) {
            if (isRtl) {
                iconLeft = (right - left) - getPaddingRight() - iconWidth;
                contentLeft = iconLeft - contentWidth;
            } else {
                iconLeft = getPaddingLeft();
                contentLeft = iconLeft + iconWidth;
            }
        } else {
            if (isRtl) {
                iconLeft = getPaddingLeft();
                contentLeft = iconLeft + iconWidth;
            } else {
                iconLeft = (right - left) - getPaddingRight() - iconWidth;
                contentLeft = iconLeft - contentWidth;
            }
        }

        mContactIconView.layout(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);

        mMessageBubble.layout(contentLeft, contentTop, contentLeft + contentWidth,
                contentTop + contentHeight);
    }

    /**
     * Fills in the data associated with this view.
     *
     * @param cursor The cursor from a MessageList that this view is in, pointing to its entry.
     */
    public void bind(final Cursor cursor) {
        bind(cursor, true, null);
    }

    /**
     * Fills in the data associated with this view.
     *
     * @param cursor The cursor from a MessageList that this view is in, pointing to its entry.
     * @param oneOnOne Whether this is a 1:1 conversation
     */
    public void bind(final Cursor cursor,
                     final boolean oneOnOne, final String selectedMessageId) {
        mOneOnOne = oneOnOne;

        // Update our UI model
        mData.bind(cursor);
        setSelected(TextUtils.equals(mData.getMessageId(), selectedMessageId));

        // Update text and image content for the view.
        updateViewContent();

        // Update colors and layout parameters for the view.
        updateViewAppearance();

        updateContentDescription();
    }

    public void setHost(final ConversationMessageViewHost host) {
        mHost = host;
    }

    /**
     * Sets a delay loader instance to manage loading / resuming of image attachments.
     */
    public void setImageViewDelayLoader(final AsyncImageViewDelayLoader delayLoader) {
        Assert.notNull(mMessageImageView);
        mMessageImageView.setDelayLoader(delayLoader);
        mMultiAttachmentView.setImageViewDelayLoader(delayLoader);
    }

    public ConversationMessageData getData() {
        return mData;
    }

    /**
     * Returns whether we should show simplified visual style for the message view (i.e. hide the
     * avatar and bubble arrow, reduce padding).
     */
    private boolean shouldShowSimplifiedVisualStyle() {
        return mData.getCanClusterWithPreviousMessage();
    }

    /**
     * Returns whether we need to show message bubble arrow. We don't show arrow if the message
     * contains media attachments or if shouldShowSimplifiedVisualStyle() is true.
     */
    private boolean shouldShowMessageBubbleArrow() {
        return !shouldShowSimplifiedVisualStyle()
                && !(mData.hasAttachments() || mMessageHasYouTubeLink);
    }

    /**
     * Returns whether we need to show a message bubble for text content.
     */
    private boolean shouldShowMessageTextBubble() {
        if (mData.hasText()) {
            return true;
        }
        final String subjectText = MmsUtils.cleanseMmsSubject(getResources(),
                mData.getMmsSubject());
        if (!TextUtils.isEmpty(subjectText)) {
            return true;
        }
        return false;
    }

    private void updateViewContent() {
        updateMessageContent();
        int titleResId = -1;
        int statusResId = -1;
        String statusText = null;
        switch(mData.getStatus()) {
            case MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING:
            case MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING:
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
            case MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
                titleResId = R.string.message_title_downloading;
                statusResId = R.string.message_status_downloading;
                break;

            case MessageData.BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD:
                if (!OsUtil.isSecondaryUser()) {
                    titleResId = R.string.message_title_manual_download;
                    if (isSelected()) {
                        statusResId = R.string.message_status_download_action;
                    } else {
                        statusResId = R.string.message_status_download;
                    }
                }
                break;

            case MessageData.BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE:
                if (!OsUtil.isSecondaryUser()) {
                    titleResId = R.string.message_title_download_failed;
                    statusResId = R.string.message_status_download_error;
                }
                break;

            case MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED:
                if (!OsUtil.isSecondaryUser()) {
                    titleResId = R.string.message_title_download_failed;
                    if (isSelected()) {
                        statusResId = R.string.message_status_download_action;
                    } else {
                        statusResId = R.string.message_status_download;
                    }
                }
                break;

            case MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND:
            case MessageData.BUGLE_STATUS_OUTGOING_SENDING:
                statusResId = R.string.message_status_sending;
                break;

            case MessageData.BUGLE_STATUS_OUTGOING_RESENDING:
            case MessageData.BUGLE_STATUS_OUTGOING_AWAITING_RETRY:
                statusResId = R.string.message_status_send_retrying;
                break;

            case MessageData.BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER:
                statusResId = R.string.message_status_send_failed_emergency_number;
                break;

            case MessageData.BUGLE_STATUS_OUTGOING_FAILED:
                // don't show the error state unless we're the default sms app
                if (PhoneUtils.getDefault().isDefaultSmsApp()) {
                    if (isSelected()) {
                        statusResId = R.string.message_status_resend;
                    } else {
                        statusResId = MmsUtils.mapRawStatusToErrorResourceId(
                                mData.getStatus(), mData.getRawTelephonyStatus());
                    }
                    break;
                }
                // FALL THROUGH HERE

            case MessageData.BUGLE_STATUS_OUTGOING_COMPLETE:
            case MessageData.BUGLE_STATUS_INCOMING_COMPLETE:
            default:
                if (!mData.getCanClusterWithNextMessage()) {
                    statusText = mData.getFormattedReceivedTimeStamp();
                }
                break;
        }

        final boolean titleVisible = (titleResId >= 0);
        if (titleVisible) {
            final String titleText = getResources().getString(titleResId);
            mTitleTextView.setText(titleText);

            final String mmsInfoText = getResources().getString(
                    R.string.mms_info,
                    Formatter.formatFileSize(getContext(), mData.getSmsMessageSize()),
                    DateUtils.formatDateTime(
                            getContext(),
                            mData.getMmsExpiry(),
                            DateUtils.FORMAT_SHOW_DATE |
                                    DateUtils.FORMAT_SHOW_TIME |
                                    DateUtils.FORMAT_NUMERIC_DATE |
                                    DateUtils.FORMAT_NO_YEAR));
            mMmsInfoTextView.setText(mmsInfoText);
            mMessageTitleLayout.setVisibility(View.VISIBLE);
        } else {
            mMessageTitleLayout.setVisibility(View.GONE);
        }

        final String subjectText = MmsUtils.cleanseMmsSubject(getResources(),
                mData.getMmsSubject());
        final boolean subjectVisible = !TextUtils.isEmpty(subjectText);

        final boolean senderNameVisible = !mOneOnOne && !mData.getCanClusterWithNextMessage()
                && mData.getIsIncoming();
        if (senderNameVisible) {
            mSenderNameTextView.setText(mData.getSenderDisplayName());
            mSenderNameTextView.setVisibility(View.VISIBLE);
        } else {
            mSenderNameTextView.setVisibility(View.GONE);
        }

        if (statusResId >= 0) {
            statusText = getResources().getString(statusResId);
        }

        // We set the text even if the view will be GONE for accessibility
        mStatusTextView.setText(statusText);
        final boolean statusVisible = !TextUtils.isEmpty(statusText);
        if (statusVisible) {
            mStatusTextView.setVisibility(View.VISIBLE);
        } else {
            mStatusTextView.setVisibility(View.GONE);
        }

        final boolean deliveredBadgeVisible =
                mData.getStatus() == MessageData.BUGLE_STATUS_OUTGOING_DELIVERED;
        mDeliveredBadge.setVisibility(deliveredBadgeVisible ? View.VISIBLE : View.GONE);

        // Update the sim indicator.
        final boolean showSimIconAsIncoming = mData.getIsIncoming() &&
                (!mData.hasAttachments() || shouldShowMessageTextBubble());
        final SubscriptionListEntry subscriptionEntry =
                mHost.getSubscriptionEntryForSelfParticipant(mData.getSelfParticipantId(),
                        true /* excludeDefault */);
        final boolean simNameVisible = subscriptionEntry != null &&
                !TextUtils.isEmpty(subscriptionEntry.displayName) &&
                !mData.getCanClusterWithNextMessage();
        if (simNameVisible) {
            final String simNameText = mData.getIsIncoming() ? getResources().getString(
                    R.string.incoming_sim_name_text, subscriptionEntry.displayName) :
                    subscriptionEntry.displayName;
            mSimNameView.setText(simNameText);
            mSimNameView.setTextColor(showSimIconAsIncoming ? getResources().getColor(
                    R.color.timestamp_text_incoming) : subscriptionEntry.displayColor);
            mSimNameView.setVisibility(VISIBLE);
        } else {
            mSimNameView.setText(null);
            mSimNameView.setVisibility(GONE);
        }

        final boolean metadataVisible = senderNameVisible || statusVisible
                || deliveredBadgeVisible || simNameVisible;
        mMessageMetadataView.setVisibility(metadataVisible ? View.VISIBLE : View.GONE);

        final boolean messageTextAndOrInfoVisible = titleVisible || subjectVisible
                || mData.hasText() || metadataVisible;
        mMessageTextAndInfoView.setVisibility(
                messageTextAndOrInfoVisible ? View.VISIBLE : View.GONE);

        if (shouldShowSimplifiedVisualStyle()) {
            mContactIconView.setVisibility(View.GONE);
            mContactIconView.setImageResourceUri(null);
        } else {
            mContactIconView.setVisibility(View.VISIBLE);
            final Uri avatarUri = AvatarUriUtil.createAvatarUri(
                    mData.getSenderProfilePhotoUri(),
                    mData.getSenderFullName(),
                    mData.getSenderNormalizedDestination(),
                    mData.getSenderContactLookupKey());
            mContactIconView.setImageResourceUri(avatarUri, mData.getSenderContactId(),
                    mData.getSenderContactLookupKey(), mData.getSenderNormalizedDestination());
        }
    }

    private void updateMessageContent() {
        // We must update the text before the attachments since we search the text to see if we
        // should make a preview youtube image in the attachments
        updateMessageText();
        updateMessageAttachments();
        updateMessageSubject();
        mMessageBubble.bind(mData);
    }

    private void updateMessageAttachments() {
        // Bind video, audio, and VCard attachments. If there are multiple, they stack vertically.
        bindAttachmentsOfSameType(sVideoFilter,
                R.layout.message_video_attachment, mVideoViewBinder, VideoThumbnailView.class);
        bindAttachmentsOfSameType(sAudioFilter,
                R.layout.message_audio_attachment, mAudioViewBinder, AudioAttachmentView.class);
        bindAttachmentsOfSameType(sVCardFilter,
                R.layout.message_vcard_attachment, mVCardViewBinder, PersonItemView.class);

        // Bind image attachments. If there are multiple, they are shown in a collage view.
        final List<MessagePartData> imageParts = mData.getAttachments(sImageFilter);
        if (imageParts.size() > 1) {
            Collections.sort(imageParts, sImageComparator);
            mMultiAttachmentView.bindAttachments(imageParts, null, imageParts.size());
            mMultiAttachmentView.setVisibility(View.VISIBLE);
        } else {
            mMultiAttachmentView.setVisibility(View.GONE);
        }

        // In the case that we have no image attachments and exactly one youtube link in a message
        // then we will show a preview.
        String youtubeThumbnailUrl = null;
        String originalYoutubeLink = null;
        if (mMessageTextHasLinks && imageParts.size() == 0) {
            CharSequence messageTextWithSpans = mMessageTextView.getText();
            final URLSpan[] spans = ((Spanned) messageTextWithSpans).getSpans(0,
                    messageTextWithSpans.length(), URLSpan.class);
            for (URLSpan span : spans) {
                String url = span.getURL();
                String youtubeLinkForUrl = YouTubeUtil.getYoutubePreviewImageLink(url);
                if (!TextUtils.isEmpty(youtubeLinkForUrl)) {
                    if (TextUtils.isEmpty(youtubeThumbnailUrl)) {
                        // Save the youtube link if we don't already have one
                        youtubeThumbnailUrl = youtubeLinkForUrl;
                        originalYoutubeLink = url;
                    } else {
                        // We already have a youtube link. This means we have two youtube links so
                        // we shall show none.
                        youtubeThumbnailUrl = null;
                        originalYoutubeLink = null;
                        break;
                    }
                }
            }
        }
        // We need to keep track if we have a youtube link in the message so that we will not show
        // the arrow
        mMessageHasYouTubeLink = !TextUtils.isEmpty(youtubeThumbnailUrl);

        // We will show the message image view if there is one attachment or one youtube link
        if (imageParts.size() == 1 || mMessageHasYouTubeLink) {
            // Get the display metrics for a hint for how large to pull the image data into
            final WindowManager windowManager = (WindowManager) getContext().
                    getSystemService(Context.WINDOW_SERVICE);
            final DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);

            final int iconSize = getResources()
                    .getDimensionPixelSize(R.dimen.conversation_message_contact_icon_size);
            final int desiredWidth = displayMetrics.widthPixels - iconSize - iconSize;

            if (imageParts.size() == 1) {
                final MessagePartData imagePart = imageParts.get(0);
                // If the image is big, we want to scale it down to save memory since we're going to
                // scale it down to fit into the bubble width. We don't constrain the height.
                final ImageRequestDescriptor imageRequest =
                        new MessagePartImageRequestDescriptor(imagePart,
                                desiredWidth,
                                MessagePartData.UNSPECIFIED_SIZE,
                                false);
                adjustImageViewBounds(imagePart);
                mMessageImageView.setImageResourceId(imageRequest);
                mMessageImageView.setTag(imagePart);
            } else {
                // Youtube Thumbnail image
                final ImageRequestDescriptor imageRequest =
                        new UriImageRequestDescriptor(Uri.parse(youtubeThumbnailUrl), desiredWidth,
                                MessagePartData.UNSPECIFIED_SIZE, true /* allowCompression */,
                                true /* isStatic */, false /* cropToCircle */,
                                ImageUtils.DEFAULT_CIRCLE_BACKGROUND_COLOR /* circleBackgroundColor */,
                                ImageUtils.DEFAULT_CIRCLE_STROKE_COLOR /* circleStrokeColor */);
                mMessageImageView.setImageResourceId(imageRequest);
                mMessageImageView.setTag(originalYoutubeLink);
            }
            mMessageImageView.setVisibility(View.VISIBLE);
        } else {
            mMessageImageView.setImageResourceId(null);
            mMessageImageView.setVisibility(View.GONE);
        }

        // Show the message attachments container if any of its children are visible
        boolean attachmentsVisible = false;
        for (int i = 0, size = mMessageAttachmentsView.getChildCount(); i < size; i++) {
            final View attachmentView = mMessageAttachmentsView.getChildAt(i);
            if (attachmentView.getVisibility() == View.VISIBLE) {
                attachmentsVisible = true;
                break;
            }
        }
        mMessageAttachmentsView.setVisibility(attachmentsVisible ? View.VISIBLE : View.GONE);
    }

    private void bindAttachmentsOfSameType(final Predicate<MessagePartData> attachmentTypeFilter,
                                           final int attachmentViewLayoutRes, final AttachmentViewBinder viewBinder,
                                           final Class<?> attachmentViewClass) {
        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        // Iterate through all attachments of a particular type (video, audio, etc).
        // Find the first attachment index that matches the given type if possible.
        int attachmentViewIndex = -1;
        View existingAttachmentView;
        do {
            existingAttachmentView = mMessageAttachmentsView.getChildAt(++attachmentViewIndex);
        } while (existingAttachmentView != null &&
                !(attachmentViewClass.isInstance(existingAttachmentView)));

        for (final MessagePartData attachment : mData.getAttachments(attachmentTypeFilter)) {
            View attachmentView = mMessageAttachmentsView.getChildAt(attachmentViewIndex);
            if (!attachmentViewClass.isInstance(attachmentView)) {
                attachmentView = layoutInflater.inflate(attachmentViewLayoutRes,
                        mMessageAttachmentsView, false /* attachToRoot */);
                attachmentView.setOnClickListener(this);
                attachmentView.setOnLongClickListener(this);
                mMessageAttachmentsView.addView(attachmentView, attachmentViewIndex);
            }
            viewBinder.bindView(attachmentView, attachment);
            attachmentView.setTag(attachment);
            attachmentView.setVisibility(View.VISIBLE);
            attachmentViewIndex++;
        }
        // If there are unused views left over, unbind or remove them.
        while (attachmentViewIndex < mMessageAttachmentsView.getChildCount()) {
            final View attachmentView = mMessageAttachmentsView.getChildAt(attachmentViewIndex);
            if (attachmentViewClass.isInstance(attachmentView)) {
                mMessageAttachmentsView.removeViewAt(attachmentViewIndex);
            } else {
                // No more views of this type; we're done.
                break;
            }
        }
    }

    private void updateMessageSubject() {
        final String subjectText = MmsUtils.cleanseMmsSubject(getResources(),
                mData.getMmsSubject());
        final boolean subjectVisible = !TextUtils.isEmpty(subjectText);

        if (subjectVisible) {
            mSubjectText.setText(subjectText);
            mSubjectView.setVisibility(View.VISIBLE);
        } else {
            mSubjectView.setVisibility(View.GONE);
        }
    }

    //add by junwang start
    //work around our wonky API by wrapping a geo permission prompt inside a regular permissionRequest.
    private static class GeoPermissionRequest extends PermissionRequest{
        private String mOrigin;
        private GeolocationPermissions.Callback mCallback;
        private static final String RESOURCE_GEO = "RESOURCE_GEO";

        public GeoPermissionRequest(String origin, GeolocationPermissions.Callback callback){
            mOrigin = origin;
            mCallback = callback;
        }

        public Uri getOrigin(){
            return Uri.parse(mOrigin);
        }

        @Override
        public String[] getResources() {
            return new String[]{this.RESOURCE_GEO};
        }

        public void grant(String[] resources){
            assert resources.length == 1;
            assert this.RESOURCE_GEO.equals(resources[0]);
            mCallback.invoke(mOrigin, true, false);
        }

        public void deny(){
            mCallback.invoke(mOrigin, false, false);
        }
    }

    /*private boolean isContactInWebViewWhiteList(String contact){
        LogUtil.d("Junwang", "DisplayDestination = "+mData.getSenderDisplayDestination());
        LogUtil.d("Junwang", "NormalizedDestination = "+mData.getSenderNormalizedDestination());

        mWVWhiteList = new HashSet<String>();
        mWVWhiteList.add("10086");

        if(mOneOnOne && mWVWhiteList.contains(contact)){
            return true;
        }

        return false;
    }*/

    // 调起支付宝并跳转到指定页面
    private void startAlipayActivity(WebView view, String url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url,
                    Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            view.getContext().startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void AliPayH5Start(WebView view, String url){
        //定义支付域名（替换成公司申请H5的域名即可）
        String realm = "http://xxx.com";

        if(/*url.startsWith("alipays:") || url.startsWith("alipay")*/
                url.startsWith("https://mclient.alipay.com/h5Continue.htm?")){
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            view.getContext().startActivity(intent);
        }

        if (url.contains("platformapi/startapp")){
            startAlipayActivity(view, url);
        }else if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                && (url.contains("platformapi") && url.contains("startapp"))) {
            startAlipayActivity(view, url);
        } else {
            view.loadUrl(url);
        }
    }

    private void WeixinH5PayStart(WebView view, String url){
        //定义支付域名（替换成公司申请H5的域名即可）
        String realm = "http://xxx.com";

        if (url.startsWith("weixin://wap/pay?") || url.startsWith("https://wx.tenpay.com")){
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            view.getContext().startActivity(intent);
        }else{
            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("Referer", realm);
            view.loadUrl(url, extraHeaders);
        }

    }

    private static String getStringFromUrl(String s) throws IOException{
        StringBuffer buffer = new StringBuffer();
        // 通过js的执行路径获取后台数据进行解析
        URL url = new URL(s);
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setDoOutput(true);
        http.setDoInput(true);
        http.setUseCaches(false);
        http.setRequestMethod("GET");
        http.connect();
        // 将返回的输入流转换成字符串
        InputStream inputStream = http.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "utf-8");
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String str = null;
        while ((str = bufferedReader.readLine()) != null) {
            buffer.append(str);
        }
        bufferedReader.close();
        inputStreamReader.close();
        // 释放资源
        inputStream.close();
        inputStream = null;
        http.disconnect();
        str = buffer.toString();
        int index = str.indexOf("(");
        String jsonString = str.substring(index + 1, str.length() -1);
        return jsonString;
    }

    private boolean isNeedH5Display(){
        if(mData != null){
            if((mData.getStatus() == MessageData.BUGLE_STATUS_INCOMING_COMPLETE)
                    && (System.currentTimeMillis() - mData.getReceivedTimeStamp())%MAX_INTERVAL_TIME >= 1){
                return true;
            }
        }
        return false;
    }

    private boolean WXPay(WebView view, String url){
        //IWXAPI api = WXAPIFactory.createWXAPI(view.getContext(), /*"你的appid"*/"wxb4ba3c02aa476ea1");

        try{
            String content = getStringFromUrl(url);
            if(content != null && content.length() > 0){
                //Log.e("get server pay params:",content);
                JSONObject json = new JSONObject(content);
                if(null != json && !json.has("retcode") ){
                    PayReq req = new PayReq();
                    //req.appId = "wxf8b4f85f3a794e77";  // 测试用appId
                    req.appId			= json.getString("appid");
                    req.partnerId		= json.getString("partnerid");
                    req.prepayId		= json.getString("prepayid");
                    req.nonceStr		= json.getString("noncestr");
                    req.timeStamp		= json.getString("timestamp");
                    req.packageValue	= json.getString("package");
                    req.sign			= json.getString("sign");
                    req.extData			= "app data"; // optional
                    IWXAPI api = WXAPIFactory.createWXAPI(view.getContext(), /*"你的appid"*/req.appId);
                    //api.registerApp();
                    api.sendReq(req);
                    return true;
                }else{
                    LogUtil.d("Junwang", "返回错误"+json.getString("retmsg"));
                    Toast.makeText(mMessageWebView.getContext(), "返回错误"+json.getString("retmsg"), Toast.LENGTH_SHORT).show();
                }
            }else{
                LogUtil.d("Junwang", "服务器请求错误");
                Toast.makeText(mMessageWebView.getContext(), "服务器请求错误", Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            LogUtil.d("Junwang", "异常："+e.getMessage());
            Toast.makeText(mMessageWebView.getContext(), "异常："+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case OPEN_LOCATION:
                    Bundle bundle = new Bundle();
                    bundle.putString("Addr", "西湖文化广场");
                    bundle.putDouble("Latitude", 30.28582);
                    bundle.putDouble("Longtitude", 120.172416);

                    Intent intent = new Intent(mMessageWebView.getContext(), BaiduMapTestActivity.class);
                    intent.putExtras(bundle);
                    //intent.putExtra("Addr","西湖文化广场");
                    mMessageWebView.getContext().startActivity(intent);
                    //mMessageWebView.getContext().startActivity(new Intent(mMessageWebView.getContext(), BaiduMapTestActivity.class));
                    break;
                case CALL_PHONE:
                    break;
                default:
                    break;
            }
        }
    };

    @JavascriptInterface
    private void nativeMethod(){
        Toast.makeText(mMessageWebView.getContext(), "Android 本地方法", Toast.LENGTH_LONG);
    }

    private class LoadUrl extends AsyncTask<String , Integer, String> {
        @Override
        protected String doInBackground(String... strings) {
            mMessageWebView.loadUrl(mUrl);
            return mUrl;
        }
    }

    private void updateMessageText() {
        final String text = mData.getText();
        if (!TextUtils.isEmpty(text)) {
            mMessageTextView.setText(text);
            if(/*mIsContactInWhiteList*/true){
                mHasWebLinks = false;

                //Vibrator vibrator = (Vibrator)this.getContext().getSystemService(Service.VIBRATOR_SERVICE);
                //vibrator.vibrate(new long[]{1000,1000,1000,1000}, -1);
                LogUtil.d("Junwang", "updateMessageText start text = "+ text);
                Linkify.TransformFilter mentionFilter = new Linkify.TransformFilter(){
                    public final String transformUrl(final Matcher match, String url){
                        LogUtil.d("Junwang", "UpdateMessageText url="+url);
                        if(url != null){
                            mHasWebLinks = true;
                        }
                        mMessageWebView = new WebView(getContext());
                        ViewGroup.LayoutParams lp = new LayoutParams(mScreenWidth-getContext().getResources().getDimensionPixelOffset(R.dimen.webview_width),
                                mScreenHeight-getContext().getResources().getDimensionPixelOffset(R.dimen.webview_heighth));
                        //ViewGroup.LayoutParams lp1 = mMessageBubble.getLayoutParams();
                        mMessageWebView.setLayoutParams(lp);
                        mMessageBubble.addView(mMessageWebView);

                        //mMessageWebView.setVisibility(View.VISIBLE);
                        mMessageWebView.setOnTouchListener(new View.OnTouchListener(){
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                ((WebView)v).requestDisallowInterceptTouchEvent(true);
//                                if(event.equals(MotionEvent.ACTION_MOVE)){
//                                    mMessageWebView.layout(1000,1500, mScreenWidth, mScreenHeight);
//                                    mMessageWebView.invalidate();
//                                }
                                return false;
                            }
                        });
                        mMessageWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
                        mMessageWebView.addJavascriptInterface(new JavaScriptInterface(mHandler), "Android");
                        WebSettings ws = mMessageWebView.getSettings();
                        ws.setRenderPriority(WebSettings.RenderPriority.HIGH);
                        ws.setJavaScriptEnabled(true);
                        ws.setAppCacheEnabled(true);
                        ws.setGeolocationEnabled(true);
                        ws.setDomStorageEnabled(true);
                        ws.setJavaScriptCanOpenWindowsAutomatically(true);
                        String cacheDirPath = mMessageWebView.getContext().getFilesDir().getAbsolutePath()+"cache/";
                        ws.setDatabasePath(cacheDirPath);
                        ws.setDatabaseEnabled(true);
                        //mMessageWebView.addJavascriptInterface();
                        ws.setUseWideViewPort(true);
                        ws.setLoadWithOverviewMode(true);
                        //ws.setBuiltInZoomControls(true);
                        ws.setSupportZoom(true);
                        //ws.setDefaultFontSize(13);
                        //ws.setCacheMode();
                        //ws.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
                        mUrl = url;
                        if(url.equals("https://vivopay.com.cn")){
                            mVivoPayButton.setVisibility(View.VISIBLE);
                            mMessageTextView.setVisibility(View.GONE);
                        }
                        else if(url.equals("https://map.baidu.com")){
                            mLocationButton.setVisibility(View.VISIBLE);
                            mLocationButton.setText("商家地址：西湖文化广场");
                            mMessageTextView.setVisibility(View.GONE);
                        }else{
                            mMessageWebView.post(new Runnable() {
                                @Override
                                public void run() {
                                    mMessageWebView.loadUrl(mUrl);
                                }
                            });
                            //mMessageWebView.loadUrl(url);
                        }
                        //mMapView.setVisibility(View.VISIBLE);
                        mMessageWebView.setWebViewClient(new WebViewClient(){
                            boolean isLoadUrl = false;
//                            @Override
//                            public void onPageStarted(WebView view, String url, Bitmap favicon) {
//                                super.onPageStarted(view, url, favicon);
//                                ViewGroup.LayoutParams lp = mMessageWebView.getLayoutParams();
//                                lp.height = mMessageBubble.getMeasuredHeight();
//                                mMessageWebView.setLayoutParams(lp);
//                            }

                            @Override
                            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                                LogUtil.d("Junwang", "loadUrl request");
                                if(request == null){
                                    return false;
                                }
                                String url = null;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    url = request.getUrl().toString();
                                } else {
                                    url = request.toString();
                                }

                                if(url == null){
                                    return true;
                                }
                                LogUtil.d("Junwang", "loadUrl url="+url);
                                if(url.startsWith("weixin://wap/pay?") || url.startsWith("https://wx.tenpay.com")){
                                    WXPay(view, url);
                                    return true;
                                }
                                try{
                                    if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://")){
                                        LogUtil.d("Junwang", "loadUrl url1="+url);
                                        /*if(WXPay(view, url)){
                                            return true;
                                        }*/

                                        final PayTask task = new PayTask((Activity)mMessageWebView.getContext());
                                        boolean isIntercepted = task.payInterceptorWithUrl(url, true, new H5PayCallback() {
                                            @Override
                                            public void onPayResult(final H5PayResultModel result) {
                                                final String url=result.getReturnUrl();
                                                if(!TextUtils.isEmpty(url)){
                                                    ((Activity)mMessageWebView.getContext()).runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            mMessageWebView.loadUrl(url);
                                                        }
                                                    });
                                                }
                                            }
                                        });

                                        LogUtil.d("Junwang", "isIntercepted = "+isIntercepted);
                                        if(!isIntercepted)
                                            mMessageWebView.loadUrl(url);
                                        //return true;
                                        if(url.equals("https://map.baidu.com")){
                                            //mMapView.setVisibility(View.VISIBLE);
                                            mLocationButton.setVisibility(View.VISIBLE);
                                        }
                                        //view.loadUrl(url);
                                        return false;
                                    }else {
                                        /*try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            mMessageWebView.getContext().startActivity(intent);
                                        } catch (ActivityNotFoundException e) {
                                            LogUtil.d("Junwang", "can't find activity to open url");
                                        }
                                        return true;*/
                                        return false;
                                    }
                                }catch(Exception e){
                                    return false;
                                }
                            }

                            @Override
                            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                LogUtil.d("Junwang", "shouldOverrideUrlLoading loadUrl url.");
                                if(url == null){
                                    return true;
                                }
                                LogUtil.d("Junwang", "loadUrl url="+url);
                                try{
                                    if(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://")){
                                        LogUtil.d("Junwang", "loadUrl url1="+url);
                                        /**
                                         * for Alipay
                                         * 推荐采用的新的二合一接口(payInterceptorWithUrl),只需调用一次
                                         */
                                        /*final PayTask task = new PayTask((Activity)mMessageWebView.getContext());
                                        boolean isIntercepted = task.payInterceptorWithUrl(url, true, new H5PayCallback() {
                                            @Override
                                            public void onPayResult(final H5PayResultModel result) {
                                                final String url=result.getReturnUrl();
                                                if(!TextUtils.isEmpty(url)){
                                                    //mMessageWebView.loadUrl(url);
                                                    ((Activity)mMessageWebView.getContext()).runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            mMessageWebView.loadUrl(url);
                                                        }
                                                    });
                                                }
                                            }
                                        });

                                        /**
                                         * 判断是否成功拦截
                                         * 若成功拦截，则无需继续加载该URL；否则继续加载
                                         */
                                        //LogUtil.d("Junwang", "isIntercepted = "+isIntercepted);
                                        //if(!isIntercepted)
                                        //    mMessageWebView.loadUrl(url);
                                        //return true;
                                        if(url.equals("https://map.baidu.com")){
                                            //mMapView.setVisibility(View.VISIBLE);
                                            mLocationButton.setVisibility(View.VISIBLE);
                                            /*mLocationButton.setOnClickListener(new OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    mMessageWebView.getContext().startActivity(new Intent(mMessageWebView.getContext(), BaiduMapTestActivity.class));
                                                }
                                            });*/
                                            //LogUtil.d("Junwang", "start BaiduMapTestActivity");
                                            //mMessageWebView.getContext().startActivity(new Intent(mMessageWebView.getContext(), BaiduMapTestActivity.class));
                                        }
                                        //view.loadUrl(url);
                                        return false;
                                    }else {
                                        try {
                                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            mMessageWebView.getContext().startActivity(intent);
                                        } catch (ActivityNotFoundException e) {
                                            LogUtil.d("Junwang", "can't find activity to open url");
                                        }
                                        return true;
                                    }
                                }catch(Exception e){
                                    return false;
                                }
                            }


                            @Nullable
                            @Override
                            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                                return super.shouldInterceptRequest(view, request);
                            }

                        });
                        mMessageWebView.setWebChromeClient(new WebChromeClient(){
                            @Nullable
                            @Override
                            public Bitmap getDefaultVideoPoster() {
                                return Bitmap.createBitmap(new int[]{Color.TRANSPARENT}, 1, 1, Bitmap.Config.ARGB_8888);
                            }

                            @Override
                            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                                onPermissionRequest(new GeoPermissionRequest(origin, callback));
                            }

                            @Override
                            public void onPermissionRequest(PermissionRequest request) {

                            }

                            @Override
                            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                                String[] mTemp = fileChooserParams.getAcceptTypes();
                                if("image/*".equals(mTemp[0])){
                                    ConversationActivity.handleShowFileChooser(filePathCallback, true);
                                }else if("video/*".equals(mTemp[0])){
                                    ConversationActivity.handleShowFileChooser(filePathCallback, false);
                                }
                                return true;
                            }

                            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                                ConversationActivity.handleOpenFileChooser(uploadMsg, true);
                            }
                            public void openFileChooser(ValueCallback<Uri> uploadMsg,String acceptType) {
                                if("image/*".equals(acceptType)){
                                    ConversationActivity.handleOpenFileChooser(uploadMsg, true);
                                }else if("video/*".equals(acceptType)){
                                    ConversationActivity.handleOpenFileChooser(uploadMsg, false);
                                }
                            }
                            public void openFileChooser(ValueCallback<Uri> uploadMsg,String acceptType, String capture) {
                                if("image/*".equals(acceptType)){
                                    ConversationActivity.handleOpenFileChooser(uploadMsg, true);
                                }else if("video/*".equals(acceptType)){
                                    ConversationActivity.handleOpenFileChooser(uploadMsg, false);
                                }
                            }
                        });
                        return url;
                    }
                };

                if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if(Linkify.addLinks(mMessageTextView, Linkify.WEB_URLS)) {
                        Linkify.addLinks(mMessageTextView, Patterns.WEB_URL, "https://", new String[]{"http://", "https://", "rtsp://"}, null, mentionFilter);
                    }else{
                        Linkify.addLinks(mMessageTextView, Linkify.ALL);
                        mMessageTextView.setVisibility(View.VISIBLE);
                    }
                }else{
                    if(Linkify.addLinks(mMessageTextView, Linkify.WEB_URLS)) {
                        LinkifyCompat.addLinks(mMessageTextView, Patterns.WEB_URL, "https://", new String[]{"http://", "https://", "rtsp://"}, null, mentionFilter);
                    }else {
                        Linkify.addLinks(mMessageTextView, Linkify.ALL);
                        mMessageTextView.setVisibility(View.VISIBLE);
                    }
                }


                //Linkify.addLinks(mMessageTextView, Patterns.WEB_URL, "https://", new String[]{"http://", "https://", "rtsp://"}, null, mentionFilter);
                //if(!mHasWebLinks){
                // Linkify phone numbers, web urls, emails, and map addresses to allow users to
                // click on them and take the default intent.
                //mMessageTextHasLinks = Linkify.addLinks(mMessageTextView, Linkify.ALL);
                //mMessageTextView.setVisibility(View.VISIBLE);
                //}
            }else{
                // Linkify phone numbers, web urls, emails, and map addresses to allow users to
                // click on them and take the default intent.
                mMessageTextHasLinks = Linkify.addLinks(mMessageTextView, Linkify.ALL);
                mMessageTextView.setVisibility(View.VISIBLE);
            }
        } else {
            mMessageTextView.setVisibility(View.GONE);
            mMessageTextHasLinks = false;
        }
    }

    private void updateViewAppearance() {
        final Resources res = getResources();
        final ConversationDrawables drawableProvider = ConversationDrawables.get();
        final boolean incoming = mData.getIsIncoming();
        final boolean outgoing = !incoming;
        final boolean showArrow =  shouldShowMessageBubbleArrow();

        final int messageTopPaddingClustered =
                res.getDimensionPixelSize(R.dimen.message_padding_same_author);
        final int messageTopPaddingDefault =
                res.getDimensionPixelSize(R.dimen.message_padding_default);
        final int arrowWidth = res.getDimensionPixelOffset(R.dimen.message_bubble_arrow_width);
        final int messageTextMinHeightDefault = res.getDimensionPixelSize(
                R.dimen.conversation_message_contact_icon_size);
        final int messageTextLeftRightPadding = res.getDimensionPixelOffset(
                R.dimen.message_text_left_right_padding);
        final int textTopPaddingDefault = res.getDimensionPixelOffset(
                R.dimen.message_text_top_padding);
        final int textBottomPaddingDefault = res.getDimensionPixelOffset(
                R.dimen.message_text_bottom_padding);

        // These values depend on whether the message has text, attachments, or both.
        // We intentionally don't set defaults, so the compiler will tell us if we forget
        // to set one of them, or if we set one more than once.
        final int contentLeftPadding, contentRightPadding;
        final Drawable textBackground;
        final int textMinHeight;
        final int textTopMargin;
        final int textTopPadding, textBottomPadding;
        final int textLeftPadding, textRightPadding;

        if (mData.hasAttachments()) {
            if (shouldShowMessageTextBubble()) {
                // Text and attachment(s)
                contentLeftPadding = incoming ? arrowWidth : 0;
                contentRightPadding = outgoing ? arrowWidth : 0;
                textBackground = drawableProvider.getBubbleDrawable(
                        isSelected(),
                        incoming,
                        false /* needArrow */,
                        mData.hasIncomingErrorStatus());
                textMinHeight = messageTextMinHeightDefault;
                textTopMargin = messageTopPaddingClustered;
                textTopPadding = textTopPaddingDefault;
                textBottomPadding = textBottomPaddingDefault;
                textLeftPadding = messageTextLeftRightPadding;
                textRightPadding = messageTextLeftRightPadding;
            } else {
                // Attachment(s) only
                contentLeftPadding = incoming ? arrowWidth : 0;
                contentRightPadding = outgoing ? arrowWidth : 0;
                textBackground = null;
                textMinHeight = 0;
                textTopMargin = 0;
                textTopPadding = 0;
                textBottomPadding = 0;
                textLeftPadding = 0;
                textRightPadding = 0;
            }
        } else {
            // Text only
            contentLeftPadding = (!showArrow && incoming) ? arrowWidth : 0;
            contentRightPadding = (!showArrow && outgoing) ? arrowWidth : 0;
            textBackground = drawableProvider.getBubbleDrawable(
                    isSelected(),
                    incoming,
                    shouldShowMessageBubbleArrow(),
                    mData.hasIncomingErrorStatus());
            textMinHeight = messageTextMinHeightDefault;
            textTopMargin = 0;
            textTopPadding = textTopPaddingDefault;
            textBottomPadding = textBottomPaddingDefault;
            if (showArrow && incoming) {
                textLeftPadding = messageTextLeftRightPadding + arrowWidth;
            } else {
                textLeftPadding = messageTextLeftRightPadding;
            }
            if (showArrow && outgoing) {
                textRightPadding = messageTextLeftRightPadding + arrowWidth;
            } else {
                textRightPadding = messageTextLeftRightPadding;
            }
        }

        // These values do not depend on whether the message includes attachments
        final int gravity = incoming ? (Gravity.START | Gravity.CENTER_VERTICAL) :
                (Gravity.END | Gravity.CENTER_VERTICAL);
        final int messageTopPadding = shouldShowSimplifiedVisualStyle() ?
                messageTopPaddingClustered : messageTopPaddingDefault;
        final int metadataTopPadding = res.getDimensionPixelOffset(
                R.dimen.message_metadata_top_padding);

        // Update the message text/info views
        ImageUtils.setBackgroundDrawableOnView(mMessageTextAndInfoView, textBackground);
        mMessageTextAndInfoView.setMinimumHeight(textMinHeight);
        final LinearLayout.LayoutParams textAndInfoLayoutParams =
                (LinearLayout.LayoutParams) mMessageTextAndInfoView.getLayoutParams();
        textAndInfoLayoutParams.topMargin = textTopMargin;

        if (UiUtils.isRtlMode()) {
            // Need to switch right and left padding in RtL mode
            mMessageTextAndInfoView.setPadding(textRightPadding, textTopPadding, textLeftPadding,
                    textBottomPadding);
            mMessageBubble.setPadding(contentRightPadding, 0, contentLeftPadding, 0);
        } else {
            mMessageTextAndInfoView.setPadding(textLeftPadding, textTopPadding, textRightPadding,
                    textBottomPadding);
            mMessageBubble.setPadding(contentLeftPadding, 0, contentRightPadding, 0);
        }

        // Update the message row and message bubble views
        setPadding(getPaddingLeft(), messageTopPadding, getPaddingRight(), 0);
        mMessageBubble.setGravity(gravity);
        updateMessageAttachmentsAppearance(gravity);

        mMessageMetadataView.setPadding(0, metadataTopPadding, 0, 0);

        updateTextAppearance();

        requestLayout();
    }

    private void updateContentDescription() {
        StringBuilder description = new StringBuilder();

        Resources res = getResources();
        String separator = res.getString(R.string.enumeration_comma);

        // Sender information
        boolean hasPlainTextMessage = !(TextUtils.isEmpty(mData.getText()) ||
                mMessageTextHasLinks);
        if (mData.getIsIncoming()) {
            int senderResId = hasPlainTextMessage
                    ? R.string.incoming_text_sender_content_description
                    : R.string.incoming_sender_content_description;
            description.append(res.getString(senderResId, mData.getSenderDisplayName()));
        } else {
            int senderResId = hasPlainTextMessage
                    ? R.string.outgoing_text_sender_content_description
                    : R.string.outgoing_sender_content_description;
            description.append(res.getString(senderResId));
        }

        if (mSubjectView.getVisibility() == View.VISIBLE) {
            description.append(separator);
            description.append(mSubjectText.getText());
        }

        if (mMessageTextView.getVisibility() == View.VISIBLE) {
            // If the message has hyperlinks, we will let the user navigate to the text message so
            // that the hyperlink can be clicked. Otherwise, the text message does not need to
            // be reachable.
            if (mMessageTextHasLinks) {
                mMessageTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            } else {
                mMessageTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                description.append(separator);
                description.append(mMessageTextView.getText());
            }
        }

        if (mMessageTitleLayout.getVisibility() == View.VISIBLE) {
            description.append(separator);
            description.append(mTitleTextView.getText());

            description.append(separator);
            description.append(mMmsInfoTextView.getText());
        }

        if (mStatusTextView.getVisibility() == View.VISIBLE) {
            description.append(separator);
            description.append(mStatusTextView.getText());
        }

        if (mSimNameView.getVisibility() == View.VISIBLE) {
            description.append(separator);
            description.append(mSimNameView.getText());
        }

        if (mDeliveredBadge.getVisibility() == View.VISIBLE) {
            description.append(separator);
            description.append(res.getString(R.string.delivered_status_content_description));
        }

        setContentDescription(description);
    }

    private void updateMessageAttachmentsAppearance(final int gravity) {
        mMessageAttachmentsView.setGravity(gravity);

        // Tint image/video attachments when selected
        final int selectedImageTint = getResources().getColor(R.color.message_image_selected_tint);
        if (mMessageImageView.getVisibility() == View.VISIBLE) {
            if (isSelected()) {
                mMessageImageView.setColorFilter(selectedImageTint);
            } else {
                mMessageImageView.clearColorFilter();
            }
        }
        if (mMultiAttachmentView.getVisibility() == View.VISIBLE) {
            if (isSelected()) {
                mMultiAttachmentView.setColorFilter(selectedImageTint);
            } else {
                mMultiAttachmentView.clearColorFilter();
            }
        }
        for (int i = 0, size = mMessageAttachmentsView.getChildCount(); i < size; i++) {
            final View attachmentView = mMessageAttachmentsView.getChildAt(i);
            if (attachmentView instanceof VideoThumbnailView
                    && attachmentView.getVisibility() == View.VISIBLE) {
                final VideoThumbnailView videoView = (VideoThumbnailView) attachmentView;
                if (isSelected()) {
                    videoView.setColorFilter(selectedImageTint);
                } else {
                    videoView.clearColorFilter();
                }
            }
        }

        // If there are multiple attachment bubbles in a single message, add some separation.
        final int multipleAttachmentPadding =
                getResources().getDimensionPixelSize(R.dimen.message_padding_same_author);

        boolean previousVisibleView = false;
        for (int i = 0, size = mMessageAttachmentsView.getChildCount(); i < size; i++) {
            final View attachmentView = mMessageAttachmentsView.getChildAt(i);
            if (attachmentView.getVisibility() == View.VISIBLE) {
                final int margin = previousVisibleView ? multipleAttachmentPadding : 0;
                ((LinearLayout.LayoutParams) attachmentView.getLayoutParams()).topMargin = margin;
                // updateViewAppearance calls requestLayout() at the end, so we don't need to here
                previousVisibleView = true;
            }
        }
    }

    private void updateTextAppearance() {
        int messageColorResId;
        int statusColorResId = -1;
        int infoColorResId = -1;
        int timestampColorResId;
        int subjectLabelColorResId;
        if (isSelected()) {
            messageColorResId = R.color.message_text_color_incoming;
            statusColorResId = R.color.message_action_status_text;
            infoColorResId = R.color.message_action_info_text;
            if (shouldShowMessageTextBubble()) {
                timestampColorResId = R.color.message_action_timestamp_text;
                subjectLabelColorResId = R.color.message_action_timestamp_text;
            } else {
                // If there's no text, the timestamp will be shown below the attachments,
                // against the conversation view background.
                timestampColorResId = R.color.timestamp_text_outgoing;
                subjectLabelColorResId = R.color.timestamp_text_outgoing;
            }
        } else {
            messageColorResId = (mData.getIsIncoming() ?
                    R.color.message_text_color_incoming : R.color.message_text_color_outgoing);
            statusColorResId = messageColorResId;
            infoColorResId = R.color.timestamp_text_incoming;
            switch(mData.getStatus()) {

                case MessageData.BUGLE_STATUS_OUTGOING_FAILED:
                case MessageData.BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER:
                    timestampColorResId = R.color.message_failed_timestamp_text;
                    subjectLabelColorResId = R.color.timestamp_text_outgoing;
                    break;

                case MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND:
                case MessageData.BUGLE_STATUS_OUTGOING_SENDING:
                case MessageData.BUGLE_STATUS_OUTGOING_RESENDING:
                case MessageData.BUGLE_STATUS_OUTGOING_AWAITING_RETRY:
                case MessageData.BUGLE_STATUS_OUTGOING_COMPLETE:
                case MessageData.BUGLE_STATUS_OUTGOING_DELIVERED:
                    timestampColorResId = R.color.timestamp_text_outgoing;
                    subjectLabelColorResId = R.color.timestamp_text_outgoing;
                    break;

                case MessageData.BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE:
                case MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED:
                    messageColorResId = R.color.message_text_color_incoming_download_failed;
                    timestampColorResId = R.color.message_download_failed_timestamp_text;
                    subjectLabelColorResId = R.color.message_text_color_incoming_download_failed;
                    statusColorResId = R.color.message_download_failed_status_text;
                    infoColorResId = R.color.message_info_text_incoming_download_failed;
                    break;

                case MessageData.BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING:
                case MessageData.BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING:
                case MessageData.BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
                case MessageData.BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
                case MessageData.BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD:
                    timestampColorResId = R.color.message_text_color_incoming;
                    subjectLabelColorResId = R.color.message_text_color_incoming;
                    infoColorResId = R.color.timestamp_text_incoming;
                    break;

                case MessageData.BUGLE_STATUS_INCOMING_COMPLETE:
                default:
                    timestampColorResId = R.color.timestamp_text_incoming;
                    subjectLabelColorResId = R.color.timestamp_text_incoming;
                    infoColorResId = -1; // Not used
                    break;
            }
        }
        final int messageColor = getResources().getColor(messageColorResId);
        mMessageTextView.setTextColor(messageColor);
        mMessageTextView.setLinkTextColor(messageColor);
        mSubjectText.setTextColor(messageColor);
        if (statusColorResId >= 0) {
            mTitleTextView.setTextColor(getResources().getColor(statusColorResId));
        }
        if (infoColorResId >= 0) {
            mMmsInfoTextView.setTextColor(getResources().getColor(infoColorResId));
        }
        if (timestampColorResId == R.color.timestamp_text_incoming &&
                mData.hasAttachments() && !shouldShowMessageTextBubble()) {
            timestampColorResId = R.color.timestamp_text_outgoing;
        }
        mStatusTextView.setTextColor(getResources().getColor(timestampColorResId));

        mSubjectLabel.setTextColor(getResources().getColor(subjectLabelColorResId));
        mSenderNameTextView.setTextColor(getResources().getColor(timestampColorResId));
    }

    /**
     * If we don't know the size of the image, we want to show it in a fixed-sized frame to
     * avoid janks when the image is loaded and resized. Otherwise, we can set the imageview to
     * take on normal layout params.
     */
    private void adjustImageViewBounds(final MessagePartData imageAttachment) {
        Assert.isTrue(ContentType.isImageType(imageAttachment.getContentType()));
        final ViewGroup.LayoutParams layoutParams = mMessageImageView.getLayoutParams();
        if (imageAttachment.getWidth() == MessagePartData.UNSPECIFIED_SIZE ||
                imageAttachment.getHeight() == MessagePartData.UNSPECIFIED_SIZE) {
            // We don't know the size of the image attachment, enable letterboxing on the image
            // and show a fixed sized attachment. This should happen at most once per image since
            // after the image is loaded we then save the image dimensions to the db so that the
            // next time we can display the full size.
            layoutParams.width = getResources()
                    .getDimensionPixelSize(R.dimen.image_attachment_fallback_width);
            layoutParams.height = getResources()
                    .getDimensionPixelSize(R.dimen.image_attachment_fallback_height);
            mMessageImageView.setScaleType(ScaleType.CENTER_CROP);
        } else {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            // ScaleType.CENTER_INSIDE and FIT_CENTER behave similarly for most images. However,
            // FIT_CENTER works better for small images as it enlarges the image such that the
            // minimum size ("android:minWidth" etc) is honored.
            mMessageImageView.setScaleType(ScaleType.FIT_CENTER);
        }
    }

    @Override
    public void onClick(final View view) {
        final Object tag = view.getTag();
        if (tag instanceof MessagePartData) {
            final Rect bounds = UiUtils.getMeasuredBoundsOnScreen(view);
            onAttachmentClick((MessagePartData) tag, bounds, false /* longPress */);
        } else if (tag instanceof String) {
            // Currently the only object that would make a tag of a string is a youtube preview
            // image
            UIIntents.get().launchBrowserForUrl(getContext(), (String) tag);
        }
    }

    @Override
    public boolean onLongClick(final View view) {
        if (view == mMessageTextView) {
            // Preemptively handle the long click event on message text so it's not handled by
            // the link spans.
            return performLongClick();
        }

        final Object tag = view.getTag();
        if (tag instanceof MessagePartData) {
            final Rect bounds = UiUtils.getMeasuredBoundsOnScreen(view);
            return onAttachmentClick((MessagePartData) tag, bounds, true /* longPress */);
        }

        return false;
    }

    @Override
    public boolean onAttachmentClick(final MessagePartData attachment,
                                     final Rect viewBoundsOnScreen, final boolean longPress) {
        return mHost.onAttachmentClick(this, attachment, viewBoundsOnScreen, longPress);
    }

    public ContactIconView getContactIconView() {
        return mContactIconView;
    }

    // Sort photos in MultiAttachLayout in the same order as the ConversationImagePartsView
    static final Comparator<MessagePartData> sImageComparator = new Comparator<MessagePartData>(){
        @Override
        public int compare(final MessagePartData x, final MessagePartData y) {
            return x.getPartId().compareTo(y.getPartId());
        }
    };

    static final Predicate<MessagePartData> sVideoFilter = new Predicate<MessagePartData>() {
        @Override
        public boolean apply(final MessagePartData part) {
            return part.isVideo();
        }
    };

    static final Predicate<MessagePartData> sAudioFilter = new Predicate<MessagePartData>() {
        @Override
        public boolean apply(final MessagePartData part) {
            return part.isAudio();
        }
    };

    static final Predicate<MessagePartData> sVCardFilter = new Predicate<MessagePartData>() {
        @Override
        public boolean apply(final MessagePartData part) {
            return part.isVCard();
        }
    };

    static final Predicate<MessagePartData> sImageFilter = new Predicate<MessagePartData>() {
        @Override
        public boolean apply(final MessagePartData part) {
            return part.isImage();
        }
    };

    interface AttachmentViewBinder {
        void bindView(View view, MessagePartData attachment);
        void unbind(View view);
    }

    final AttachmentViewBinder mVideoViewBinder = new AttachmentViewBinder() {
        @Override
        public void bindView(final View view, final MessagePartData attachment) {
            ((VideoThumbnailView) view).setSource(attachment, mData.getIsIncoming());
        }

        @Override
        public void unbind(final View view) {
            ((VideoThumbnailView) view).setSource((Uri) null, mData.getIsIncoming());
        }
    };

    final AttachmentViewBinder mAudioViewBinder = new AttachmentViewBinder() {
        @Override
        public void bindView(final View view, final MessagePartData attachment) {
            final AudioAttachmentView audioView = (AudioAttachmentView) view;
            audioView.bindMessagePartData(attachment, mData.getIsIncoming(), isSelected());
            audioView.setBackground(ConversationDrawables.get().getBubbleDrawable(
                    isSelected(), mData.getIsIncoming(), false /* needArrow */,
                    mData.hasIncomingErrorStatus()));
        }

        @Override
        public void unbind(final View view) {
            ((AudioAttachmentView) view).bindMessagePartData(null, mData.getIsIncoming(), false);
        }
    };

    final AttachmentViewBinder mVCardViewBinder = new AttachmentViewBinder() {
        @Override
        public void bindView(final View view, final MessagePartData attachment) {
            final PersonItemView personView = (PersonItemView) view;
            personView.bind(DataModel.get().createVCardContactItemData(getContext(),
                    attachment));
            personView.setBackground(ConversationDrawables.get().getBubbleDrawable(
                    isSelected(), mData.getIsIncoming(), false /* needArrow */,
                    mData.hasIncomingErrorStatus()));
            final int nameTextColorRes;
            final int detailsTextColorRes;
            if (isSelected()) {
                nameTextColorRes = R.color.message_text_color_incoming;
                detailsTextColorRes = R.color.message_text_color_incoming;
            } else {
                nameTextColorRes = mData.getIsIncoming() ? R.color.message_text_color_incoming
                        : R.color.message_text_color_outgoing;
                detailsTextColorRes = mData.getIsIncoming() ? R.color.timestamp_text_incoming
                        : R.color.timestamp_text_outgoing;
            }
            personView.setNameTextColor(getResources().getColor(nameTextColorRes));
            personView.setDetailsTextColor(getResources().getColor(detailsTextColorRes));
        }

        @Override
        public void unbind(final View view) {
            ((PersonItemView) view).bind(null);
        }
    };

    /**
     * A helper class that allows us to handle long clicks on linkified message text view (i.e. to
     * select the message) so it's not handled by the link spans to launch apps for the links.
     */
    private static class IgnoreLinkLongClickHelper implements OnLongClickListener, OnTouchListener {
        private boolean mIsLongClick;
        private final OnLongClickListener mDelegateLongClickListener;

        /**
         * Ignore long clicks on linkified texts for a given text view.
         * @param textView the TextView to ignore long clicks on
         * @param longClickListener a delegate OnLongClickListener to be called when the view is
         *        long clicked.
         */
        public static void ignoreLinkLongClick(final TextView textView,
                                               @Nullable final OnLongClickListener longClickListener) {
            final IgnoreLinkLongClickHelper helper =
                    new IgnoreLinkLongClickHelper(longClickListener);
            textView.setOnLongClickListener(helper);
            textView.setOnTouchListener(helper);
        }

        private IgnoreLinkLongClickHelper(@Nullable final OnLongClickListener longClickListener) {
            mDelegateLongClickListener = longClickListener;
        }

        @Override
        public boolean onLongClick(final View v) {
            // Record that this click is a long click.
            mIsLongClick = true;
            if (mDelegateLongClickListener != null) {
                return mDelegateLongClickListener.onLongClick(v);
            }
            return false;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && mIsLongClick) {
                // This touch event is a long click, preemptively handle this touch event so that
                // the link span won't get a onClicked() callback.
                mIsLongClick = false;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mIsLongClick = false;
            }
            return false;
        }
    }
}
