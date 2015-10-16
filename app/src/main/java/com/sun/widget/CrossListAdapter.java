package com.sun.widget;

import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapter for CrossList
 * Created by yw_sun on 2015/9/10.
 */
public abstract class CrossListAdapter {
    public abstract int getRowCount();
    public abstract int getColumnCount();
    public abstract View getTableHeaderView();
    public abstract View getRowTitleView(int pos, View convertView, ViewGroup parent);
    public abstract View getColumnTitleView(int pos, View convertView, ViewGroup parent);
    public abstract View getContentView(int row, int col, View convertView, ViewGroup parent);
    public abstract Object getRowTitleItem(int pos);
    public abstract Object getColumnTitleItem(int pos);
    public abstract Object getContentItem(int row, int col);

    /** DataSetObserver with params */
    static public interface DataSetObserver {
        public void onChanged(ArrayList<Pair<Integer, Integer>> paramList);
        public void onInvalidated(ArrayList<Pair<Integer, Integer>> paramList);
    }

    private Set<DataSetObserver> mObserverSet = new HashSet<DataSetObserver>();
    public void addDataSetObserver(DataSetObserver observer) {
        mObserverSet.add(observer);
    }
    public void removeDataSetObserver(DataSetObserver observer) {
        mObserverSet.remove(observer);
    }

    /**
     * Notify list to refresh specific items
     * @param paramList A list with items like Pair<rowIndex, colIndex>; index -1 for row/col title
     */
    public void notifyDataSetChanged(ArrayList<Pair<Integer, Integer>> paramList) {
        for(DataSetObserver observer : mObserverSet) {
            observer.onChanged(paramList);
        }
    }
    public void notifyDataSetInvalidated(ArrayList<Pair<Integer, Integer>> paramList) {
        for(DataSetObserver observer : mObserverSet) {
            observer.onInvalidated(paramList);
        }
    }
}
