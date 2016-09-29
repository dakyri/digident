package com.mayaswell.digident;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

import com.mayaswell.digident.CatalogAPI.CatalogItem;
/**
 * maintains a cache of retreived catalog items, and a short list of a fixed number of the most recent
 */
public class ItemCache {
	private final int maxRecents;
	protected HashMap<String, CatalogItem> cache = new HashMap<String, CatalogItem>();
	protected LinkedList<CatalogItem> mostRecent = new LinkedList<CatalogItem>();
	public String highestId = null;
	public String lowestId = null;

	public ItemCache(int mr) {
		maxRecents = mr;
	}
	public void add(Collection<CatalogItem> set) {
		for (CatalogItem m: set) {
			boolean found = false;
			found = cache.containsKey(m.id);
			if (!found) {
				mostRecent.add(m);
				if (mostRecent.size() > maxRecents) {
					mostRecent.remove();
				}
				if (highestId == null || m.id.compareTo(highestId) > 0) {
					highestId = m.id;
				}
				if (lowestId == null || m.id.compareTo(lowestId) < 0) {
					lowestId = m.id;
				}
				cache.put(m.id, m);
			}
		}
	}

	public Collection<CatalogItem> getList() {
		return cache.values();
	}

	public Collection<CatalogItem> getRecentList() {
		return mostRecent;
	}

	public void clear() {
		highestId = null;
		lowestId = null;
		cache.clear();
		mostRecent.clear();
	}

	public int size() {
		return cache.size();
	}
}
