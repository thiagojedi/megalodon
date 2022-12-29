package org.joinmastodon.android.ui.displayitems;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.BaseStatusListFragment;
import org.joinmastodon.android.fragments.ProfileFragment;
import org.joinmastodon.android.fragments.ThreadFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.DisplayItemsParent;
import org.joinmastodon.android.model.Filter;
import org.joinmastodon.android.model.Notification;
import org.joinmastodon.android.model.Poll;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.PhotoLayoutHelper;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import me.grishka.appkit.Nav;
import me.grishka.appkit.imageloader.requests.ImageLoaderRequest;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.views.UsableRecyclerView;

public abstract class StatusDisplayItem{
	public final String parentID;
	public final BaseStatusListFragment parentFragment;
	public boolean inset;
	public int index;

	public StatusDisplayItem(String parentID, BaseStatusListFragment parentFragment){
		this.parentID=parentID;
		this.parentFragment=parentFragment;
	}

	public abstract Type getType();

	public int getImageCount(){
		return 0;
	}

	public ImageLoaderRequest getImageRequest(int index){
		return null;
	}

	public static BindableViewHolder<? extends StatusDisplayItem> createViewHolder(Type type, Activity activity, ViewGroup parent){
		return switch(type){
			case HEADER -> new HeaderStatusDisplayItem.Holder(activity, parent);
			case REBLOG_OR_REPLY_LINE -> new ReblogOrReplyLineStatusDisplayItem.Holder(activity, parent);
			case TEXT -> new TextStatusDisplayItem.Holder(activity, parent);
			case PHOTO -> new PhotoStatusDisplayItem.Holder(activity, parent);
			case GIFV -> new GifVStatusDisplayItem.Holder(activity, parent);
			case AUDIO -> new AudioStatusDisplayItem.Holder(activity, parent);
			case VIDEO -> new VideoStatusDisplayItem.Holder(activity, parent);
			case POLL_OPTION -> new PollOptionStatusDisplayItem.Holder(activity, parent);
			case POLL_FOOTER -> new PollFooterStatusDisplayItem.Holder(activity, parent);
			case CARD -> new LinkCardStatusDisplayItem.Holder(activity, parent);
			case FOOTER -> new FooterStatusDisplayItem.Holder(activity, parent);
			case ACCOUNT_CARD -> new AccountCardStatusDisplayItem.Holder(activity, parent);
			case ACCOUNT -> new AccountStatusDisplayItem.Holder(activity, parent);
			case HASHTAG -> new HashtagStatusDisplayItem.Holder(activity, parent);
			case GAP -> new GapStatusDisplayItem.Holder(activity, parent);
			case EXTENDED_FOOTER -> new ExtendedFooterStatusDisplayItem.Holder(activity, parent);
		};
	}

	public static ArrayList<StatusDisplayItem> buildFilteredItems(BaseStatusListFragment fragment, Status status, DisplayItemsParent parentObject, Filter appliedFilter) {
		ArrayList<StatusDisplayItem> items=new ArrayList<>();
		String parentID=parentObject.getID();

		items.add(new TextStatusDisplayItem(parentID, "Filtered: "+appliedFilter.title, fragment, status));

		return items;
	}

	public static ArrayList<StatusDisplayItem> buildItems(BaseStatusListFragment fragment, Status status, String accountID, DisplayItemsParent parentObject, Map<String, Account> knownAccounts, boolean inset, boolean addFooter, Notification notification){
		String parentID=parentObject.getID();
		ArrayList<StatusDisplayItem> items=new ArrayList<>();
		Status statusForContent=status.getContentStatus();
		Bundle args=new Bundle();
		args.putString("account", accountID);
		if(status.reblog!=null){
			boolean isOwnPost = AccountSessionManager.getInstance().isSelf(fragment.getAccountID(), status.account);
			items.add(new ReblogOrReplyLineStatusDisplayItem(parentID, fragment, fragment.getString(R.string.user_boosted, status.account.displayName), status.account.emojis, R.drawable.ic_fluent_arrow_repeat_all_20_filled, isOwnPost ? status.visibility : null, i->{
				args.putParcelable("profileAccount", Parcels.wrap(status.account));
				Nav.go(fragment.getActivity(), ProfileFragment.class, args);
			}));
		}else if(status.inReplyToAccountId!=null && knownAccounts.containsKey(status.inReplyToAccountId)){
			Account account=Objects.requireNonNull(knownAccounts.get(status.inReplyToAccountId));
			items.add(new ReblogOrReplyLineStatusDisplayItem(parentID, fragment, fragment.getString(R.string.in_reply_to, account.displayName), account.emojis, R.drawable.ic_fluent_arrow_reply_20_filled, null, i->{
				args.putParcelable("profileAccount", Parcels.wrap(account));
				Nav.go(fragment.getActivity(), ProfileFragment.class, args);
			}));
		}
		HeaderStatusDisplayItem header;
		items.add(header=new HeaderStatusDisplayItem(parentID, statusForContent.account, statusForContent.createdAt, fragment, accountID, statusForContent, null, notification));
		if(!TextUtils.isEmpty(statusForContent.content))
			items.add(new TextStatusDisplayItem(parentID, HtmlParser.parse(statusForContent.content, statusForContent.emojis, statusForContent.mentions, statusForContent.tags, accountID), fragment, statusForContent));
		else
			header.needBottomPadding=true;
		List<Attachment> imageAttachments=statusForContent.mediaAttachments.stream().filter(att->att.type.isImage()).collect(Collectors.toList());
		if(!imageAttachments.isEmpty()){
			int photoIndex=0;
			PhotoLayoutHelper.TiledLayoutResult layout=PhotoLayoutHelper.processThumbs(1000, 1910, imageAttachments);
			for(Attachment attachment:imageAttachments){
				if(attachment.type==Attachment.Type.IMAGE){
					items.add(new PhotoStatusDisplayItem(parentID, statusForContent, attachment, fragment, photoIndex, imageAttachments.size(), layout, layout.tiles[photoIndex]));
				}else if(attachment.type==Attachment.Type.GIFV){
					items.add(new GifVStatusDisplayItem(parentID, statusForContent, attachment, fragment, photoIndex, imageAttachments.size(), layout, layout.tiles[photoIndex]));
				}else if(attachment.type==Attachment.Type.VIDEO){
					items.add(new VideoStatusDisplayItem(parentID, statusForContent, attachment, fragment, photoIndex, imageAttachments.size(), layout, layout.tiles[photoIndex]));
				}else{
					throw new IllegalStateException("This isn't supposed to happen, type is "+attachment.type);
				}
				photoIndex++;
			}
		}
		for(Attachment att:statusForContent.mediaAttachments){
			if(att.type==Attachment.Type.AUDIO){
				items.add(new AudioStatusDisplayItem(parentID, fragment, statusForContent, att));
			}
		}
		if(statusForContent.poll!=null){
			buildPollItems(parentID, fragment, statusForContent.poll, items);
		}
		if(statusForContent.card!=null && statusForContent.mediaAttachments.isEmpty() && TextUtils.isEmpty(statusForContent.spoilerText)){
			items.add(new LinkCardStatusDisplayItem(parentID, fragment, statusForContent));
		}
		if(addFooter){
			items.add(new FooterStatusDisplayItem(parentID, fragment, statusForContent, accountID));
			if(status.hasGapAfter && !(fragment instanceof ThreadFragment))
				items.add(new GapStatusDisplayItem(parentID, fragment));
		}
		int i=1;
		for(StatusDisplayItem item:items){
			item.inset=inset;
			item.index=i++;
		}
		return items;
	}

	public static void buildPollItems(String parentID, BaseStatusListFragment fragment, Poll poll, List<StatusDisplayItem> items){
		for(Poll.Option opt:poll.options){
			items.add(new PollOptionStatusDisplayItem(parentID, poll, opt, fragment));
		}
		items.add(new PollFooterStatusDisplayItem(parentID, fragment, poll));
	}

	public enum Type{
		HEADER,
		REBLOG_OR_REPLY_LINE,
		TEXT,
		PHOTO,
		VIDEO,
		GIFV,
		AUDIO,
		POLL_OPTION,
		POLL_FOOTER,
		CARD,
		FOOTER,
		ACCOUNT_CARD,
		ACCOUNT,
		HASHTAG,
		GAP,
		EXTENDED_FOOTER
	}

	public static abstract class Holder<T extends StatusDisplayItem> extends BindableViewHolder<T> implements UsableRecyclerView.DisableableClickable{
		public Holder(View itemView){
			super(itemView);
		}

		public Holder(Context context, int layout, ViewGroup parent){
 			super(context, layout, parent);
		}

		public String getItemID(){
			return item.parentID;
		}

		@Override
		public void onClick(){
			item.parentFragment.onItemClick(item.parentID);
		}

		@Override
		public boolean isEnabled(){
			return item.parentFragment.isItemEnabled(item.parentID);
		}
	}
}
