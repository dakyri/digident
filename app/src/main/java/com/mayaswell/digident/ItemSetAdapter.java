package com.mayaswell.digident;

import android.graphics.Movie;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import com.mayaswell.digident.CatalogAPI.CatalogItem;
import com.mayaswell.digident.databinding.CatalogListItemBinding;

import java.util.ArrayList;
import java.util.Collection;

/**
 * main adapter for the list view recycler
 */
public class ItemSetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	/**
	 * view holder for catalog item
	 */
	public static class ItemViewHolder extends RecyclerView.ViewHolder {
		public CatalogListItemBinding binding;
		protected RelativeLayout main;
		private CatalogItem item;

		public ItemViewHolder(CatalogListItemBinding v) {
			super(v.getRoot());
			binding = v;
			main = (RelativeLayout) v.getRoot();
			item = null;
		}

		public void setToItem(CatalogItem item) {
			this.item = item;
			binding.setItem(item);
		}
	}

	protected ArrayList<Object> dataSet = new ArrayList<Object>();

	/**
	 * clear current list and notify changes
	 */
	public void clear() {
		dataSet.clear();
		notifyDataSetChanged();
	}

	/**
	 * cleare current list, add all items to it, and notify all items as changed
	 * @param list
	 */
	public void addAll(Collection<?> list) {
		dataSet.clear();
		dataSet.addAll(list);
		notifyDataSetChanged();
	}

	/**
	 * update all items from the given list, and notify changes to all after 'current'
	 * @param mCurrent
	 * @param list
	 */
	public void updateFrom(int mCurrent, Collection<?> list) {
		dataSet.clear();
		dataSet.addAll(list);
		for (int i = mCurrent; i < list.size(); i++) {
			notifyItemChanged(i);
		}
	}

	/**
	 * default constructor
	 */
	public ItemSetAdapter() {
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		RecyclerView.ViewHolder vh = null;
		CatalogListItemBinding binding = CatalogListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
		vh = new ItemViewHolder(binding);
		return vh;
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
		CatalogItem ci = (CatalogItem) dataSet.get(position);
		((ItemViewHolder) holder).setToItem(ci);
	}

	@Override
	public int getItemCount() {
		return dataSet.size();
	}


	@Override
	public int getItemViewType(int position) {
		return 0;
	}
}
