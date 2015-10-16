package com.sun.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;


/**
 * Cross List View
 * usage:
 * <code>
 *     //init CrossList
 *     CrossList crossList = (CrossList)findViewById(R.id.cross_list_view);
 *     CrossListAdapter adapter = new CrossListAdapter(){
 *         @Override
 *         public int getRowCount(){...}
 *         @Override
 *         public int getColumnCount(){...}
 *         @Override
 *         public View getRowTitleView(int pos, View convertView ViewGroup parent) {...}
 *         @Override
 *         public View getColumnTitleView(int pos, View convertView, ViewGroup parent){...}
 *         @Override
 *         public View getContentView(int row, int col, View convertView, ViewGroup parent){...}
 *         @Override
 *         public Object getRowTitleItem(int pos){...}
 *         @Override
 *         public Object getColumnTitleItem(int pos){...}
 *         @Override
 *         public Object getContentItem(int row, int col){...}
 *     };
 *     crossList.setAdapter(adapter);
 *
 *     //Bind click listener
 *     crossList.setItemClickListener(new CrossList.OnItemClickListener(){
 *         ...
 *     });
 *
 *     //Refresh view at {row:1, col:1} and {row:1, col:2}
 *     ArrayList<Pair<Integer, Integer>> refreshList = new ArrayList<Pair<Integer, Integer>>();
 *     refreshList.add(new Pair(1, 1));
 *     refreshList.add(new Pair(1, 2));
 *     adapter.notifyDataSetInvalidated(refreshList);
 * </code>
 */
public class CrossList extends ViewGroup {
    /** Logic Constants */
    private static final int Row = 1;
    private static final int Column = 2;

    /** Basic View Attrs */
    private  int mRowTitleHeight = 50;
    private  int mRowTitleWidth = 140; //content width
    private  int mColumnTitleHeight = 70; // content height
    private  int mColumnTitleWidth = 50;

    /** Dynamic View Attrs */
    private Point mTitleOriginPoint = new Point(0, 0);
    private Point mContentOriginPoint = new Point(0, 0);

    /**
     * View trackers are used to keep track on child views in CrossList because
     * that invisible children must be removed to make layout-process cost less
     */
    private HashMap<Integer, View> mRowTitleTracker = new HashMap<Integer, View>();//Pair<rowIndex, itemView>
    private HashMap<Integer, View> mColTitleTracker = new HashMap<Integer, View>();//Pair<colIndex, itemView>
    private HashMap<Integer, View> mContentTracker = new HashMap<Integer, View>();//Pair<contentKey, contentView>
    private RelativeLayout mTableHeaderTracker = null;

    /**
     * Recyclers for title and content
     */
    private HashSet<View> mRowTitleRecycler = new HashSet<View>();
    private HashSet<View> mColTitleRecycler = new HashSet<View>();
    private HashSet<View> mContentRecycler = new HashSet<View>();

    private void pushRowTitleRecycler(View child) {
        if(mRowTitleRecycler.contains(child)) {
            return ;
        }
        child.setVisibility(View.INVISIBLE);
        mRowTitleRecycler.add(child);
    }
    private View pollRowTitleRecycler() {
        if(mRowTitleRecycler.isEmpty()) {
            return null;
        }
        View res = mRowTitleRecycler.iterator().next();
        mRowTitleRecycler.remove(res);
        res.setVisibility(View.VISIBLE);
        return res;
    }
    private void pushColTitleRecycler(View child) {
        if(mColTitleRecycler.contains(child)) {
            return ;
        }
        child.setVisibility(View.INVISIBLE);
        mColTitleRecycler.add(child);
    }
    private View pollColTitleRecycler(){
        if(mColTitleRecycler.isEmpty()) {
            return null;
        }
        View res = mColTitleRecycler.iterator().next();
        mColTitleRecycler.remove(res);
        res.setVisibility(View.VISIBLE);
        return res;
    }
    private void pushContentRecycler(View child) {
        if(mContentRecycler.contains(child)) {
            return ;
        }
        child.setVisibility(View.INVISIBLE);
        mContentRecycler.add(child);
    }
    private View pollContentRecycler(){
        if(mContentRecycler.isEmpty()) {
            return null;
        }
        View res = mContentRecycler.iterator().next();
        mContentRecycler.remove(res);
        res.setVisibility(View.VISIBLE);
        return res;
    }


    static private int getKey(int row, int col){
        return (row<<16)+col;
    }
    static private Pair<Integer/*row*/, Integer/*col*/> getRowAndCol(int key) {
        return new Pair(key>>16, key&0xffff);
    }

    private CrossListAdapter.DataSetObserver mObserver = new CrossListAdapter.DataSetObserver() {
        @Override
        public void onChanged(ArrayList<Pair<Integer, Integer>> paramList) {

        }
        @Override
        public void onInvalidated(ArrayList<Pair<Integer, Integer>> paramList) {
            if(paramList == null) {
                return ;
            }
            for(Pair<Integer, Integer> entry : paramList) {
                int row = entry.first;
                int col = entry.second;
                if(row < 0) {
                    View convertView = mRowTitleTracker.get(col);
                    if(convertView != null) {
                        mAdapter.getRowTitleView(col, convertView, CrossList.this);
                    }
                } else if(col < 0) {
                    View convertView = mColTitleTracker.get(row);
                    if(convertView != null) {
                        mAdapter.getColumnTitleView(row, convertView, CrossList.this);
                    }
                } else {
                    View convertView = mContentTracker.get(getKey(row, col));
                    if(convertView != null) {
                        mAdapter.getContentView(row, col, convertView, CrossList.this);
                    }
                }
            }
        }
    };
    private CrossListAdapter mAdapter = null;
    public void setAdapter(CrossListAdapter adapter) {
        if(mAdapter != null) {
            mAdapter.removeDataSetObserver(mObserver);
        }
        mAdapter = adapter;
        if(mAdapter != null) {
            mAdapter.addDataSetObserver(mObserver);
        }
        refreshView();
    }
    public CrossListAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Destroy the original cross list and recreate
     * This method is quite expensive
     * Try to use adapter.notifyDataSetInvalidate if just need to refresh some items
     */
    public void refreshView() {
        if(mAdapter == null) {
            return ;
        }
        //clear previous cache
        mTableHeaderTracker = null;
        mRowTitleTracker.clear();
        mColTitleTracker.clear();
        mContentTracker.clear();
        mRowTitleRecycler.clear();
        mColTitleRecycler.clear();
        mContentRecycler.clear();
        mScrollBound = null;
        mTableHeadBottomShadow = null;
        mTableHeadRightShadow = null;
        mRowTitleShadow = null;
        mColTitleShadow = null;
        this.removeAllViewsInLayout();

        for(int i=0;i<mOverScrollViews.length;i++) {
            if(mOverScrollViews[i] != null) {
                setOverScrollView(mOverScrollViews[i], i);
            }
        }
        mFlingStack = 0;
        mTitleOriginPoint = new Point();
        mContentOriginPoint = new Point();
        mPendingScroll = null;
        mDisableTouch = false;
        mIsTouchScrolling = false;
        mHasTouchRelease = false;
        mIsSmoothlyMovingX = false;
        mIsSmoothlyMovingY = false;
        mBounceEnable = true;
        requestLayout();
    }

    public CrossList(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ary = context.obtainStyledAttributes(attrs, R.styleable.CrossList);
        mRowTitleWidth = (int)ary.getDimension(R.styleable.CrossList_rowTitleWidth, mRowTitleWidth);
        mRowTitleHeight = (int)ary.getDimension(R.styleable.CrossList_rowTitleHeight, mRowTitleHeight);
        mColumnTitleWidth = (int)ary.getDimension(R.styleable.CrossList_columnTitleWidth, mColumnTitleWidth);
        mColumnTitleHeight = (int)ary.getDimension(R.styleable.CrossList_columnTitleHeight, mColumnTitleHeight);

        mRowTitleShadowSize = (int)ary.getDimension(R.styleable.CrossList_rowTitleShadowSize, mRowTitleShadowSize);
        mColTitleShadowSize = (int)ary.getDimension(R.styleable.CrossList_columnTitleShadowSize, mColTitleShadowSize);
        mRowTitleShadowRes = ary.getResourceId(R.styleable.CrossList_rowTitleShadowRes, 0);
        mColTitleShadowRes = ary.getResourceId(R.styleable.CrossList_columnTitleShadowRes, 0);
        mTableHeadBottomShadowRes = ary.getResourceId(R.styleable.CrossList_tableHeadBottomShadowRes, 0);
        mTableHeadRightShadowRes = ary.getResourceId(R.styleable.CrossList_tableHeadRightShadowRes, 0);

        ary.recycle();
    }
    public CrossList(Context context) {
        super(context);
    }

    public CrossList(Context context, int rowTitleWidth, int rowTitleHeight, int colTitleWidth, int colTitleHeight) {
        super(context);
        mRowTitleWidth = rowTitleWidth;
        mRowTitleHeight = rowTitleHeight;
        mColumnTitleWidth = colTitleWidth;
        mColumnTitleHeight = colTitleHeight;
    }

    private void addView4RowTitle(View child) {
        int viewIndex = this.getChildCount()-1;
        addAndMeasureChild(child, viewIndex, mRowTitleWidth, mRowTitleHeight);
    }
    private void addView4ColTitle(View child) {
        int viewIndex = this.getChildCount()-1;
        addAndMeasureChild(child, viewIndex, mColumnTitleWidth, mColumnTitleHeight);
    }
    private void addView4Content(View child) {
        int viewIndex = 0;
        addAndMeasureChild(child, Math.max(0, viewIndex), mRowTitleWidth, mColumnTitleHeight);
    }

    private void addAndMeasureChild(View child, int index, int width, int height) {
        if(child == null) {
            return ;
        }
        ViewGroup.LayoutParams layoutParam = child.getLayoutParams();
        if(layoutParam == null) {
            layoutParam = new ViewGroup.LayoutParams(width, height);
        }
        int widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        child.measure(widthMeasureSpec, heightMeasureSpec);
        this.addViewInLayout(child, index, layoutParam);
    }

    /**
     * Get current drawing rect of view in at (col, row);
     * Refresh mOriginPoint to change the position
     * @param row -1 means title
     * @param col -1 means title
     * @return drawing rect
     */
    private Rect getViewLocByIndex(int row, int col) {
        //XXX:currently calc from a formula
        final int paddingTop = getPaddingTop();
        final int paddingLeft = getPaddingLeft();
        int left, top, right, bottom;
        //calc logic top and bottom
        if(row < 0) {
            top = paddingTop;
            bottom = top + mRowTitleHeight;
        } else {
            top = mRowTitleHeight + mColumnTitleHeight * row;
            bottom = top + mColumnTitleHeight;
        }
        //calc logic left and right
        if(col < 0) {
            left = paddingLeft;
            right = left + mColumnTitleWidth;
        } else {
            left = mColumnTitleWidth + mRowTitleWidth * col;
            right = left + mRowTitleWidth;

        }
        //calc real drawing position
        if(row < 0 && col < 0){
            //table title never moves
        } else if(row < 0) {
            left += mTitleOriginPoint.x;
            right += mTitleOriginPoint.x;
        } else if(col < 0) {
            top += mTitleOriginPoint.y;
            bottom += mTitleOriginPoint.y;
        } else {
            left += mContentOriginPoint.x;
            right += mContentOriginPoint.x;
            top += mContentOriginPoint.y;
            bottom += mContentOriginPoint.y;
        }
        return new Rect(left, top, right, bottom);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if(mAdapter == null) {
            return ;
        }

        /** Deal pending scroll */
        if(mPendingScroll != null) {
            mPendingScroll.run();
            mPendingScroll = null;
        }

        updateBounceBusiness();

        /** Refresh layout tracker to prepare layout */
        Rect indexBound = getVisibleBoundIndex();
        updateRowTitle(indexBound.left, indexBound.right);
        updateColTitle(indexBound.top, indexBound.bottom);
        updateContentView(indexBound.left, indexBound.top, indexBound.right, indexBound.bottom);
        updateOverScrollViews();
        updateRowAndColShadows();

        /** Start layout according to trackers */
        layoutRowTitleByTracker();
        layoutColTitleByTracker();
        layoutContentByTracker();

        /** Add table header if needed */
        updateTableHeader();
    }

    private void updateRowTitle(int leftIndex, int rightIndex) {
        //remove invisible row title
        ArrayList<Integer> removeList = new ArrayList<Integer>();
        for(Map.Entry<Integer,View> entry : mRowTitleTracker.entrySet()) {
            int index = entry.getKey();
            if(index >= leftIndex && index <= rightIndex) {
                //visible, do nothing
            } else {
                pushRowTitleRecycler(entry.getValue());
                removeList.add(entry.getKey());
            }
        }
        for(int i : removeList) {
            mRowTitleTracker.remove(i);
        }
        //add new row titles
        for(int index=leftIndex; index<=rightIndex; index++) {
            if(!mRowTitleTracker.containsKey(index)) { //not visible yet
                View convertView = pollRowTitleRecycler();
                View newChild = mAdapter.getRowTitleView(index, convertView, this);
                if(newChild != convertView) {
                    addView4RowTitle(newChild);
                }
                mRowTitleTracker.put(index, newChild);
            }
        }
    }

    private void updateColTitle(int topIndex, int bottomIndex) {
        //remove invisible row title
        ArrayList<Integer> removeList = new ArrayList<Integer>();
        for(Map.Entry<Integer,View> entry : mColTitleTracker.entrySet()) {
            int index = entry.getKey();
            if(index >= topIndex && index <= bottomIndex) {
                //visible, do nothing
            } else {
                pushColTitleRecycler(entry.getValue());
                removeList.add(entry.getKey());
            }
        }
        for(int i : removeList) {
            mColTitleTracker.remove(i);
        }
        //add new row titles
        for(int index=topIndex; index<=bottomIndex; index++) {
            if(!mColTitleTracker.containsKey(index)) { //not visible yet
                View convertView = pollColTitleRecycler();
                View newChild = mAdapter.getColumnTitleView(index, convertView, this);
                if(newChild != convertView) {
                    addView4ColTitle(newChild);
                }
                mColTitleTracker.put(index, newChild);
            }
        }
    }

    /** update content, params shows the current visible bound by index*/
    private void updateContentView(int leftIndex, int topIndex, int rightIndex, int bottomIndex) {
        //remove invis children
        ArrayList<Integer> removeList = new ArrayList<Integer>();
        for(Map.Entry<Integer, View> entry : mContentTracker.entrySet()) {
            Pair<Integer, Integer> rowAndCol = getRowAndCol(entry.getKey());
            if(rowAndCol.first>=topIndex && rowAndCol.first<=bottomIndex && rowAndCol.second>=leftIndex && rowAndCol.second<=rightIndex ) {
                //visible, do nothing
            } else {
                //invisible, remove
                pushContentRecycler(entry.getValue());
                removeList.add(entry.getKey());
            }
        }
        for(int key : removeList) {
            mContentTracker.remove(key);
        }
        //add children
        for(int col=leftIndex;col<=rightIndex;col++) {
            for(int row=topIndex;row<=bottomIndex;row++) {
                int key = getKey(row, col);
                View child = mContentTracker.get(key);
                if(child == null) { //not visible yet
                    View convertView = pollContentRecycler();
                    child = mAdapter.getContentView(row, col, convertView, this);
                    if (child != convertView) {//new view, add and measure
                        addView4Content(child);
                    }
                    mContentTracker.put(key, child);
                }
            }
        }
    }

    /** layout business */
    private void layoutRowTitleByTracker() {
        for(Map.Entry<Integer, View> entry : mRowTitleTracker.entrySet()) {
            Rect viewRect = getViewLocByIndex(-1, entry.getKey());
            View child = entry.getValue();
            if(child != null) {
                child.layout(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom);
            }
        }
    }
    private void layoutColTitleByTracker() {
        for(Map.Entry<Integer, View> entry : mColTitleTracker.entrySet()) {
            Rect viewRect = getViewLocByIndex(entry.getKey(), -1);
            View child = entry.getValue();
            if(child != null) {
                child.layout(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom);
            }
        }
    }
    private void layoutContentByTracker() {
        for(Map.Entry<Integer, View> entry : mContentTracker.entrySet()) {
            Pair<Integer, Integer> rowAndCol = getRowAndCol(entry.getKey());
            Rect viewRect = getViewLocByIndex(rowAndCol.first, rowAndCol.second);
            View child = entry.getValue();
            if(child != null) {
                child.layout(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom);
            }
        }
    }

    /**
     * Override {#dispatchTouchEvent} to prevent that children's
     * {#onTouch} might return true and invalidate the parent's touch events
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if(onTouch(event) == false) {
            return super.dispatchTouchEvent(event);
        } else {
            return true;
        }
    }
    private Pair<Integer/*row*/, Integer/*col*/> getIndexByPoint(int x, int y) {
        int row = -1, col = -1;
        Rect indexBound = getVisibleBoundIndex();
        int left = getViewLocByIndex(-1, indexBound.left).left;
        int right = getViewLocByIndex(-1, indexBound.right).right;
        int top = getViewLocByIndex(indexBound.top, -1).top;
        int bottom = getViewLocByIndex(indexBound.bottom, -1).bottom;

        col = indexBound.left + (int)((x-left)*1.f/(right-left)*mRowTitleTracker.size());
        row = indexBound.top + (int)((y-top)*1.f/(bottom-top)*mColTitleTracker.size());
        //Deal with the clip of (rowTitle vs. topItem) or (colTitle vs. leftItem)
        Rect viewLeftTop = getViewLocByIndex(-1, -1);
        if(x < viewLeftTop.right) {
            col = -1;
        }
        if(y < viewLeftTop.bottom) {
            row = -1;
        }
        return new Pair(row, col);
    }

    private boolean onTouch(MotionEvent event) {
        if(mDisableTouch) { //Block touch events when bouncing
            return false;
        }
        if(event.getAction() == MotionEvent.ACTION_UP) {
            mIsTouchScrolling = false;
            mHasTouchRelease = true;
            requestLayout();
        }
        if(mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(event);
        }
        return false;
    }
    private  GestureDetector.OnGestureListener mListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if(mItemClickListener == null) {
                return false;
            }
            Pair<Integer, Integer> rowAndCol = getIndexByPoint((int)e.getX(), (int)e.getY());
            if(rowAndCol.first < 0) {
                mItemClickListener.onRowTitleClicked(CrossList.this, rowAndCol.second);
            } else if(rowAndCol.second < 0) {
                mItemClickListener.onColumnTitleClicked(CrossList.this, rowAndCol.first);
            } else {
                mItemClickListener.onContentItemClicked(CrossList.this, rowAndCol.first, rowAndCol.second);
            }
            //Don't catch click event
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mIsTouchScrolling = true;
            pushBouncyScroll(mContentOriginPoint, distanceX, distanceY, mMaxOverScrollX, mMaxOverScrollY);
            mTitleOriginPoint.set(mContentOriginPoint.x, mContentOriginPoint.y);
            fixBound(mTitleOriginPoint);
            requestLayout();
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //Fix speed to avoid unnecessary fling
            double velocity = Math.sqrt(velocityX*velocityX+velocityY*velocityY);
            if(velocity<1000) {
                return true;
            }
            if(velocity>6000) {
                float decrease = (float)(6000.0/velocity);
                velocityX *= decrease;
                velocityY *= decrease;
            }
            startFling((int) velocityX, (int) velocityY);
            return true;
        }
    };
    /**
     * Gesture Dealing: Only for scroll and fling
     */
    private GestureDetector mGestureDetector = new GestureDetector(mListener);

    /**
     * Get the over-scroll distance( >=0 ) for a given origin point
     * @param originPoint the given origin point
     * @return rect.left for left over scroll.Positive if over-scrolled, negative not
     */
    private Rect mOverRect = new Rect();
    public Rect getOverScrollRect(Point originPoint) {
        Rect scrollBound = getScrollBound();
        mOverRect.left = originPoint.x - scrollBound.left;
        mOverRect.top = originPoint.y - scrollBound.top;
        mOverRect.right = -originPoint.x-scrollBound.right;
        mOverRect.bottom = -originPoint.y-scrollBound.bottom;
        return mOverRect;
    }

    /**
     * The direction of over-scroll views
     * OVER_SCROLL_LEFT means the view shows when drag left too much
     */
    static public final int OVER_SCROLL_LEFT = 0;
    static public final int OVER_SCROLL_TOP = 1;
    static public final int OVER_SCROLL_RIGHT = 2;
    static public final int OVER_SCROLL_BOTTOM = 3;
    private View[] mOverScrollViews = {null, null, null, null};
    private void layoutOverScrollView(int type) {
        if(type < 0 || type >= mOverScrollViews.length) {
            return ;
        }
        View view = mOverScrollViews[type];
        if(view == null) {
            return ;
        }
        int viewWidth = view.getMeasuredWidth();
        int viewHeight = view.getMeasuredHeight();
        Rect tableHead = getViewLocByIndex(-1, -1);
        Rect leftTopContent = getViewLocByIndex(0, 0);
        Rect rightBottomContent = getViewLocByIndex(mAdapter.getRowCount() - 1, mAdapter.getColumnCount() - 1);
        int top = 0,left = 0;
        switch(type) {
            case OVER_SCROLL_LEFT:
                top = tableHead.bottom;
                left = leftTopContent.left - viewWidth;
                break;
            case OVER_SCROLL_TOP:
                top = leftTopContent.top - viewHeight;
                left = tableHead.right;
                break;
            case OVER_SCROLL_RIGHT:
                top = tableHead.bottom;
                left = rightBottomContent.right;
                break;
            case OVER_SCROLL_BOTTOM:
                top = rightBottomContent.bottom;
                left = tableHead.right;
                break;
        }
        view.layout(left, top, left+viewWidth, top+viewHeight);
    }
    /**
     * Set a over-scroll view to crossList
     * @param view the over-scroll view
     * @param type index like #OVER_SCROLL_LEFT
     */
    private LinkedList<Pair<Integer, View>> mOverScrollViewStack = new LinkedList<Pair<Integer, View>>();
    public void setOverScrollView(View view, int type) {
        mOverScrollViewStack.push(new Pair(type, view));
        if(type < 0 || type >= mOverScrollViews.length) {
            return ;
        }
    }
    private void updateOverScrollViews() {
        for(Pair<Integer, View> entry : mOverScrollViewStack) {
            int type = entry.first;
            View view = entry.second;
            if(view != null) {
                if(mOverScrollViews[type] != null) {
                    removeViewInLayout(mOverScrollViews[type]);
                }
                ViewGroup.LayoutParams lp;
                if(type == OVER_SCROLL_LEFT || type == OVER_SCROLL_RIGHT) {
                    lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                    view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                            MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
                    mMaxOverScrollX = view.getMeasuredWidth()*4;
                } else {
                    lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    view.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    mMaxOverScrollY = view.getMeasuredHeight()*4;
                }
                this.addViewInLayout(view, 0, lp);
                mOverScrollViews[type] = view;
            }
        }
        mOverScrollViewStack.clear();
    }

    /**
     * #mScrollBound is to describe the bound of mOriginPoint which presents scroller of the view
     * Don't use mScrollBound directly, use getScrollBound instead
     */
    Rect mScrollBound = null;
    private Rect getScrollBound() {
        if(mScrollBound == null) {
            //calc scroll bound
            mScrollBound = new Rect();
            Rect bottomRight = getViewLocByIndex(mAdapter.getRowCount()-1, mAdapter.getColumnCount()-1);
            mScrollBound.left = 0;
            mScrollBound.top = 0;
            mScrollBound.right = bottomRight.right - getMeasuredWidth() - mContentOriginPoint.x;
            mScrollBound.bottom = bottomRight.bottom - getMeasuredHeight() - mContentOriginPoint.y;
        }
        return mScrollBound;
    }
    private void pushHardScroll(Point originPoint, float scrollX, float scrollY) {
        originPoint.x -= scrollX;
        originPoint.y -= scrollY;
        fixBound(originPoint);
    }
    private boolean mDisableTouch = false;
    private boolean mIsTouchScrolling = false;
    private boolean mHasTouchRelease = false;
    private float mMaxOverScrollX = 200.f;
    private float mMaxOverScrollY = 200.f;
    private float calcBouncyDistance(float delta, float overscroll, float maxOverScroll) {
        float x = delta, S = overscroll, D = maxOverScroll;
        return 0.7f*(x - (S/D)*x - x*x/(2*D));
    }
    private void pushBouncyScroll(Point originPoint, float scrollX, float scrollY, float maxOverScrollX, float maxOverScrollY) {
        //Judge if over scroll
        Rect scrollBound = getScrollBound();
        Rect overRect = getOverScrollRect(originPoint);

        //adjust X
        if(overRect.left - scrollX > 0 && scrollX < 0) {
            //pull right, left over
            float delta = 0;
            //fix x to none hard bound
            if(overRect.left < 0) {
                // just left over
                delta = overRect.left - scrollX;
                originPoint.x -= overRect.left;
                overRect.left = 0;
            } else {
                delta = -scrollX;
            }
            delta = calcBouncyDistance(delta, overRect.left, maxOverScrollX);
            originPoint.x += delta;
        } else if(overRect.right + scrollX > 0 && scrollX > 0){
            //pull left, right over
            float delta = 0;
            //fix x to none hard bound
            if(overRect.right < 0) {
                // just right over
                delta = overRect.right + scrollX;
                originPoint.x += overRect.right;
                overRect.right = 0;
            } else {
                delta = scrollX;
            }
            delta = calcBouncyDistance(delta, overRect.right, maxOverScrollX);
            originPoint.x -= delta;
        } else {
            originPoint.x -= scrollX;
        }
        originPoint.x = Math.min(originPoint.x, (int) (scrollBound.left + maxOverScrollX));
        originPoint.x = Math.max(originPoint.x, (int) (-scrollBound.right-maxOverScrollX));

        //adjust Y
        if(overRect.top - scrollY > 0 && scrollY < 0) {
            //pull down, top over
            float delta = 0;
            //fix y to none hard bound
            if(overRect.top < 0) {
                // just top over
                delta = overRect.top - scrollY;
                originPoint.y += overRect.top;
                overRect.top = 0;
            } else {
                delta = -scrollY;
            }
            delta = calcBouncyDistance(delta, overRect.top, maxOverScrollY);
            originPoint.y += delta;
        } else if(overRect.bottom + scrollY > 0 && scrollY > 0){
            //pull up, bottom over
            float delta = 0;
            //fix y to none hard bound
            if(overRect.bottom < 0) {
                // just right over
                delta = overRect.bottom + scrollY;
                originPoint.y += overRect.bottom;
                overRect.bottom = 0;
            } else {
                delta = scrollY;
            }
            delta = calcBouncyDistance(delta, overRect.bottom, maxOverScrollY);
            originPoint.y -= delta;
        } else {
            originPoint.y -= scrollY;
        }
        originPoint.y = Math.min(originPoint.y, (int) (scrollBound.top + maxOverScrollY));
        originPoint.y = Math.max(originPoint.y, (int) (-scrollBound.bottom - maxOverScrollY));
    }
    private void updateBounceBusiness() {
        /** check over scroll for 4 directions and trigger their events */
        Rect overRect = getOverScrollRect(mContentOriginPoint);
        boolean hasOverScroll = false;
        ArrayList<OverScrollModel> overScrollList4By = new ArrayList<OverScrollModel>();
        ArrayList<OverScrollModel> overScrollList4Release = new ArrayList<OverScrollModel>();
        if(overRect.left > 0) {
            hasOverScroll = true;
            if(mOverScrollViews[OVER_SCROLL_LEFT] != null) {
                mOverScrollViews[OVER_SCROLL_LEFT].setVisibility(View.VISIBLE);
                layoutOverScrollView(OVER_SCROLL_LEFT);
                if(mOverScrollListener != null) {
                    float degree = overRect.left * 1.f/mOverScrollViews[OVER_SCROLL_LEFT].getMeasuredWidth();
                    if(mIsTouchScrolling) {
                        overScrollList4By.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_LEFT], OVER_SCROLL_LEFT, degree));
                    }
                    if(mHasTouchRelease) {
                        overScrollList4Release.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_LEFT], OVER_SCROLL_LEFT, degree));
                    }
                }
            }
        } else {
            if(mOverScrollViews[OVER_SCROLL_LEFT] != null) {
                mOverScrollViews[OVER_SCROLL_LEFT].setVisibility(View.INVISIBLE);
            }
        }
        if(overRect.right > 0) {
            hasOverScroll = true;
            if(mOverScrollViews[OVER_SCROLL_RIGHT] != null) {
                mOverScrollViews[OVER_SCROLL_RIGHT].setVisibility(View.VISIBLE);
                layoutOverScrollView(OVER_SCROLL_RIGHT);
                if(mOverScrollListener != null) {
                    float degree = overRect.right * 1.f/mOverScrollViews[OVER_SCROLL_RIGHT].getMeasuredWidth();
                    if(mIsTouchScrolling) {
                        overScrollList4By.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_RIGHT], OVER_SCROLL_RIGHT, degree));
                    }
                    if(mHasTouchRelease) {
                        overScrollList4Release.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_RIGHT], OVER_SCROLL_RIGHT, degree));
                    }
                }
            }
        } else {
            if(mOverScrollViews[OVER_SCROLL_RIGHT] != null) {
                mOverScrollViews[OVER_SCROLL_RIGHT].setVisibility(View.INVISIBLE);
            }
        }
        if(overRect.top > 0) {
            hasOverScroll = true;
            if(mOverScrollViews[OVER_SCROLL_TOP] != null) {
                mOverScrollViews[OVER_SCROLL_TOP].setVisibility(View.VISIBLE);
                layoutOverScrollView(OVER_SCROLL_TOP);
                if(mOverScrollListener != null) {
                    float degree = overRect.top * 1.f/mOverScrollViews[OVER_SCROLL_TOP].getMeasuredHeight();
                    if(mIsTouchScrolling) {
                        overScrollList4By.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_TOP], OVER_SCROLL_TOP, degree));
                    }
                    if(mHasTouchRelease) {
                        overScrollList4Release.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_TOP], OVER_SCROLL_TOP, degree));
                    }
                }
            }
        } else {
            if(mOverScrollViews[OVER_SCROLL_TOP] != null) {
                mOverScrollViews[OVER_SCROLL_TOP].setVisibility(View.INVISIBLE);
            }
        }
        if(overRect.bottom > 0) {
            hasOverScroll = true;
            if(mOverScrollViews[OVER_SCROLL_BOTTOM] != null) {
                mOverScrollViews[OVER_SCROLL_BOTTOM].setVisibility(View.VISIBLE);
                layoutOverScrollView(OVER_SCROLL_BOTTOM);
                if(mOverScrollListener != null) {
                    float degree = overRect.bottom * 1.f/mOverScrollViews[OVER_SCROLL_BOTTOM].getMeasuredHeight();
                    if(mIsTouchScrolling) {
                        overScrollList4By.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_BOTTOM], OVER_SCROLL_BOTTOM, degree));
                    }
                    if(mHasTouchRelease) {
                        overScrollList4Release.add(new OverScrollModel(mOverScrollViews[OVER_SCROLL_BOTTOM], OVER_SCROLL_BOTTOM, degree));
                    }
                }
            }
        } else {
            if(mOverScrollViews[OVER_SCROLL_BOTTOM] != null) {
                mOverScrollViews[OVER_SCROLL_BOTTOM].setVisibility(View.INVISIBLE);
            }
        }
        if(overScrollList4By.size() != 0) {
            mOverScrollListener.onScrollOverBy(this, overScrollList4By);
        }
        if(overScrollList4Release.size() != 0) {
            mOverScrollListener.onScrollOverRelease(this, overScrollList4Release);
        }

        //clear ACTION_UP
        mHasTouchRelease = false;

        //bounce back if needed
        if(!mIsTouchScrolling && mFlingStack == 0 && hasOverScroll && mBounceEnable) {
            int deltaX = mTitleOriginPoint.x - mContentOriginPoint.x;
            int deltaY = mTitleOriginPoint.y - mContentOriginPoint.y;
            smoothlyMoveBy(deltaX, deltaY);
        }
    }

    private final long BounceTime = 300;
    /**
     * Start a scroll animation to a fixed point
     * @param deltaX 0 if don't move
     * @param deltaY 0 if don't move
     * @param originPoint the origin point to be moved
     * @return true if start success, else false
     */
    private boolean mIsSmoothlyMovingX = false;
    private boolean mIsSmoothlyMovingY = false;
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean smoothlyMoveBy(final int deltaX, final int deltaY) {
        if(deltaX == 0 && deltaY == 0) {
            //necessary movement
            return false;
        }
        if(mIsSmoothlyMovingX && deltaX != 0 || mIsSmoothlyMovingY && deltaY != 0) {
            //other movement processing
            return false;
        }
        mIsSmoothlyMovingX = (deltaX != 0);
        mIsSmoothlyMovingY = (deltaY != 0);
        final Point startPoint = new Point(mContentOriginPoint);
        final ValueAnimator animator = ValueAnimator.ofFloat(0.f, 1.f);
        animator.setDuration(BounceTime);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float k = (Float) animation.getAnimatedValue();
                if(deltaX !=0 ) {
                    mContentOriginPoint.x = startPoint.x + (int) (deltaX * k);
                }
                if(deltaY != 0) {
                    mContentOriginPoint.y = startPoint.y + (int) (deltaY * k);
                }
                mTitleOriginPoint.set(mContentOriginPoint.x, mContentOriginPoint.y);
                fixBound(mTitleOriginPoint);
                requestLayout();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }
            @Override
            public void onAnimationEnd(Animator animation) {
                if(mIsSmoothlyMovingX && deltaX != 0) {
                    mIsSmoothlyMovingX = false;
                }
                if(mIsSmoothlyMovingY && deltaY != 0) {
                    mIsSmoothlyMovingY = false;
                }
                requestLayout();
            }
            @Override
            public void onAnimationCancel(Animator animation) {

            }
            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();
        return true;
    }

    public void showOverScrollView(int type) {
        if(type < 0 || type >= mOverScrollViews.length || mOverScrollViews[type] == null) {
            return ;
        }
        View view = mOverScrollViews[type];
        Rect scrollBound = getScrollBound();
        if(type == OVER_SCROLL_LEFT) {
            int deltaX = scrollBound.left + view.getMeasuredWidth() - mContentOriginPoint.x;
            smoothlyMoveBy(deltaX, 0);
        } else if(type == OVER_SCROLL_RIGHT) {
            int deltaX = scrollBound.right - getMeasuredWidth() - view.getMeasuredWidth() - mContentOriginPoint.x;
            smoothlyMoveBy(deltaX, 0);
        } else if(type == OVER_SCROLL_TOP) {
            int deltaY = scrollBound.top + view.getMeasuredHeight() - mContentOriginPoint.y;
            smoothlyMoveBy(0, deltaY);
        } else if(type == OVER_SCROLL_BOTTOM) {
            int deltaY = scrollBound.bottom - getMeasuredHeight() - view.getMeasuredHeight() - mContentOriginPoint.y;
            smoothlyMoveBy(0, deltaY);
        }
    }
    public void hideOverScrollView(int type) {
        if(type < 0 || type >= mOverScrollViews.length || mOverScrollViews[type] == null) {
            return ;
        }
        if(type == OVER_SCROLL_LEFT || type == OVER_SCROLL_RIGHT) {
            int deltaX = mTitleOriginPoint.x - mContentOriginPoint.x;
            smoothlyMoveBy(deltaX, 0);
        } else {
            int deltaY = mTitleOriginPoint.y - mContentOriginPoint.y;
            smoothlyMoveBy(0, deltaY);
        }
    }

    private Runnable mPendingScroll = null;
    /**
     * Scroll to ensure item at (row, col) will be conspicuous
     * @param row the row index of the item
     * @param col the col index of the item
     */
    public void scrollTo(final int row, final int col) {
        mPendingScroll = new Runnable() {
            @Override
            public void run() {
                Rect curRect = getViewLocByIndex(row, col);
                Point dstPoint = new Point(getMeasuredWidth()/2, getMeasuredHeight()/2);
                pushHardScroll(mTitleOriginPoint, curRect.left - dstPoint.x, curRect.top - dstPoint.y);
                pushHardScroll(mContentOriginPoint, curRect.left - dstPoint.x, curRect.top - dstPoint.y);
            }
        };
        requestLayout();
    }

    /**
     * Maintain fling business
     * XXX:fling values show be configurable
     */
    private long mFlingFrame = 0;
    private Point mPendingFling = new Point();
    private int mFlingStack = 0;//Used to ensure only one fling work at a time
    private boolean mHoldFling;
    private void startFling(int velocX, int velocY) {
        if(mFlingStack == 0) { //No fling currently, start new fling
            mFlingStack = 1;
            mHoldFling = true;

            final int dirX = velocX>0?1:-1;
            final int dirY = velocY>0?1:-1;
            velocX = Math.abs(velocX);
            velocY = Math.abs(velocY);
            final Point curSpeed = new Point(velocX, velocY);
            final float normalResistX = velocX*1.5f;
            final float normalResistY = velocY*1.5f;
            mFlingFrame = System.currentTimeMillis();
            final int boundBackSpeed = 400;// px/s

            this.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //Check if the current fling is canceled
                    if(mHoldFling == false) {
                        mFlingStack = 0;
                        return ;
                    }
                    if(mFlingStack > 1) { //New fling pending
                        mFlingStack = 0;
                        startFling(mPendingFling.x, mPendingFling.y);
                        return ;
                    }
                    //Else update current fling
                    long curFrame = System.currentTimeMillis();
                    long interval = curFrame - mFlingFrame;
                    mFlingFrame = curFrame;

                    float deltaX = dirX * curSpeed.x * interval / 1000;
                    float deltaY = dirY * curSpeed.y * interval / 1000;
                    pushBouncyScroll(mContentOriginPoint, -deltaX, -deltaY, mMaxOverScrollX, mMaxOverScrollY);
                    Rect overRect = getOverScrollRect(mContentOriginPoint);
                    //update speed,add resist if bouncy
                    float resistX = normalResistX;
                    float resistY = normalResistY;
                    if(overRect.left > 0 && dirX > 0 && curSpeed.x > 0 ||
                            overRect.right> 0 && dirX < 0 && curSpeed.x > 0) {
                        float over = overRect.left>0?overRect.left:overRect.right;
                        if(over > mMaxOverScrollX/2) {
                            curSpeed.x = 0;
                        } else {
                            resistX += normalResistX * 2 * (over/mMaxOverScrollX);
                        }
                    }
                    if(overRect.top > 0 && dirY > 0 && curSpeed.y > 0 ||
                            overRect.bottom > 0 && dirY < 0 && curSpeed.y > 0) {
                        float over = overRect.top>0?overRect.top:overRect.bottom;
                        if(over > mMaxOverScrollY/2) {
                            curSpeed.y = 0;
                        }else {
                            resistY += normalResistY * 2 * (over/mMaxOverScrollY);
                        }
                    }
                    curSpeed.x = (int)Math.max(curSpeed.x - resistX*interval/1000, 0);
                    curSpeed.y = (int)Math.max(curSpeed.y - resistY*interval/1000, 0);

                    //try to bounce back if needed
                    float bounceBack = boundBackSpeed*interval/1000;
                    if(overRect.left > 0 && curSpeed.x == 0) {
                        float bounceBackX = Math.min(overRect.left, bounceBack);
                        mContentOriginPoint.x -= bounceBackX;
                        overRect.left -= bounceBackX;
                    } else if(overRect.right > 0 && curSpeed.x == 0) {
                        float bounceBackX = Math.min(overRect.right, bounceBack);
                        mContentOriginPoint.x += bounceBackX;
                        overRect.right -= bounceBackX;
                    }
                    if(overRect.top > 0 && curSpeed.y == 0) {
                        float bounceBackY = Math.min(overRect.top, bounceBack);
                        mContentOriginPoint.y -= bounceBackY;
                        overRect.top -= bounceBackY;
                    } else if(overRect.bottom > 0 && curSpeed.y == 0) {
                        float bounceBackY = Math.min(overRect.bottom, bounceBack);
                        mContentOriginPoint.y += bounceBackY;
                        overRect.bottom -= bounceBackY;
                    }

                    mTitleOriginPoint.set(mContentOriginPoint.x, mContentOriginPoint.y);
                    fixBound(mTitleOriginPoint);
                    requestLayout();

                    if(curSpeed.x == 0 && curSpeed.y == 0 &&
                            overRect.left <= 0 && overRect.right <= 0 && overRect.top <= 0 && overRect.bottom <= 0) {
                        //a fling is finish
                        mFlingStack--;
                    } else { //start next frame
                        CrossList.this.postDelayed(this, 16);
                    }
                }
            }, 16);
        } else {
            //Add a pending fling work
            mFlingStack++;
            mPendingFling.set(velocX, velocY);
        }
    }
    /**
     * Fix an origin point according to scroll bound
     * @param originPoint the point to be adjusted
     */
    private void fixBound(Point originPoint) {
        Rect scrollBound = getScrollBound();
        originPoint.x = Math.max(Math.min(-scrollBound.left, originPoint.x), -scrollBound.right);
        originPoint.y = Math.max(Math.min(-scrollBound.top, originPoint.y), -scrollBound.bottom);
    }

    /** Item events */
    static public interface OnItemClickListener{
        /**
         * Perform when a row title is clicked
         * @param listView the parent cross list
         * @param index row index of clicked item
         */
        public void onRowTitleClicked(CrossList listView, int index);
        /**
         * Perform when a column title is clicked
         * @param listView the parent cross list
         * @param index column index of clicked item
         */
        public void onColumnTitleClicked(CrossList listView, int index);
        /**
         * Perform when a content item is clicked
         * @param listView the parent cross list
         * @param row row index for clicked item
         * @param col col index of clicked item
         */
        public void onContentItemClicked(CrossList listView, int row, int col);
    }
    private OnItemClickListener mItemClickListener = null;
    public void setItemClickListener(OnItemClickListener listener) {
        mItemClickListener = listener;
    }

    /**
     * Table head shadows:enable shadow bound between titles and contents
     */
    private View mTableHeadBottomShadow = null;
    private View mTableHeadRightShadow = null;
    private View mRowTitleShadow = null;
    private View mColTitleShadow = null;
    private int mRowTitleShadowSize = 10;
    private int mColTitleShadowSize = 10;

    private int mTableHeadBottomShadowRes = 0;
    private int mTableHeadRightShadowRes = 0;
    private int mRowTitleShadowRes = 0;
    private int mColTitleShadowRes = 0;

    private void updateRowAndColShadows() {
        if(mRowTitleShadowRes != 0 && mRowTitleShadow == null) {
            mRowTitleShadow = new View(getContext());
            Rect tableHead = getViewLocByIndex(-1, -1);
            int shadowWidth = getMeasuredWidth() - tableHead.width();
            addAndMeasureChild(mRowTitleShadow, -1, shadowWidth, mRowTitleShadowSize);
            mRowTitleShadow.setBackgroundResource(mRowTitleShadowRes);
            mRowTitleShadow.layout(tableHead.right, tableHead.bottom,
                    tableHead.right + shadowWidth, tableHead.bottom + mRowTitleShadowSize);
        }
        if(mColTitleShadowRes != 0 && mColTitleShadow == null) {
            mColTitleShadow = new View(getContext());
            Rect tableHead = getViewLocByIndex(-1, -1);
            int shadowHeight = getMeasuredHeight() - tableHead.height();
            addAndMeasureChild(mColTitleShadow, -1, mColTitleShadowSize, shadowHeight);
            mColTitleShadow.setBackgroundResource(mColTitleShadowRes);
            mColTitleShadow.layout(tableHead.right, tableHead.bottom,
                    tableHead.right+mColTitleShadowSize, tableHead.bottom+shadowHeight);
        }
    }

    /**
     * Update table head view and its shadows
     */
    private void updateTableHeader() {
        if(mTableHeaderTracker == null) {
            //add a container for table head to enable shadow
            mTableHeaderTracker = new RelativeLayout(getContext());
            mTableHeaderTracker.setBackgroundColor(0x00000000);

            Rect headRect = getViewLocByIndex(-1, -1);
            RelativeLayout.LayoutParams headParam = new RelativeLayout.LayoutParams(headRect.width(), headRect.height());
            View tableHead = mAdapter.getTableHeaderView();
            tableHead.setLayoutParams(headParam);
            tableHead.measure(MeasureSpec.makeMeasureSpec(headRect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(headRect.height(), MeasureSpec.EXACTLY));
            mTableHeaderTracker.addView(tableHead);

            if(mTableHeadBottomShadowRes != 0 && mTableHeadBottomShadow == null) {
                mTableHeadBottomShadow = new View(getContext());
                RelativeLayout.LayoutParams bottomParam = new RelativeLayout.LayoutParams(headRect.width()+mColTitleShadowSize,mRowTitleShadowSize);
                bottomParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                mTableHeadBottomShadow.measure(MeasureSpec.makeMeasureSpec(bottomParam.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(bottomParam.height, MeasureSpec.EXACTLY));
                mTableHeadBottomShadow.setLayoutParams(bottomParam);
                mTableHeadBottomShadow.setBackgroundResource(mTableHeadBottomShadowRes);
                mTableHeaderTracker.addView(mTableHeadBottomShadow);
            }
            if(mTableHeadRightShadowRes != 0 && mTableHeadRightShadow == null) {
                mTableHeadRightShadow = new View(getContext());
                RelativeLayout.LayoutParams rightParam = new RelativeLayout.LayoutParams(mColTitleShadowSize,headRect.height()+mRowTitleShadowSize);
                rightParam.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                mTableHeadRightShadow.measure(MeasureSpec.makeMeasureSpec(rightParam.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rightParam.height, MeasureSpec.EXACTLY));
                mTableHeadRightShadow.setLayoutParams(rightParam);
                mTableHeadRightShadow.setBackgroundResource(mTableHeadRightShadowRes);
                mTableHeaderTracker.addView(mTableHeadRightShadow);
            }

            addAndMeasureChild(mTableHeaderTracker, -1, headRect.width() + mColTitleShadowSize, headRect.height() + mRowTitleShadowSize);
            mTableHeaderTracker.layout(headRect.left, headRect.top, headRect.right+mColTitleShadowSize, headRect.bottom+mRowTitleShadowSize);
        }
    }

    /**
     * Enable outer to supervise the over scroll of CrossList
     * When scroll reaches the bound of CrossList, the OverScrollListener's methods will be
     * triggered to make outside able to handle over scroll events.
     * For that more than one side could be over-scrolled at the same time, methods deliver
     * every over-scroll side as params.
     * NOTE:In every single layout event, methods will be triggered only ONCE
     */
    static public class OverScrollModel{
        /**
         * over-scroll-pixels / overView-pixels , always positive,
         * equals 1.f when overView is just totally shown
         */
        public final float overDegree;
        /**
         * @see CrossList#OVER_SCROLL_LEFT
         */
        public final int type;
        /**
         * The view shows when over-scrolled
         * @see CrossList#OVER_SCROLL_LEFT
         */
        public final View overView;
        public OverScrollModel(View overView, int type, float overDegree) {
            this.overView = overView;
            this.type = type;
            this.overDegree = overDegree;
        }
    }
    static public interface OverScrollListener{
        /**
         * Trigger when layout is over-scrolled
         * @param overScrollList the over-scroll-models for currently over-scrolled parts
         * @return
         */
        public void onScrollOverBy(CrossList list, ArrayList<OverScrollModel> overScrollList);
        /**
         * Trigger when layout's over-scroll is released
         * @param overScrollList the over-scroll-models for currently over-scrolled parts
         * @return
         */
        public void onScrollOverRelease(CrossList list, ArrayList<OverScrollModel> overScrollList);
    }
    private OverScrollListener mOverScrollListener = null;
    /**
     * Set the over-scroll listener
     * @see CrossList.OverScrollModel
     * @param listener
     * @return
     */
    public void setOverScrollListener(OverScrollListener listener) {
        mOverScrollListener = listener;
    }

    /**
     * Get the visible bound by index
     * XXX:Indexes are calculated by a formula
     */
    public Rect getVisibleBoundIndex() {
        Rect leftTopRect = getViewLocByIndex(-1, -1);
        int boundLeft = leftTopRect.right;
        int boundRight = getMeasuredWidth() - getPaddingRight();
        int boundTop = leftTopRect.bottom;
        int boundBottom = getMeasuredHeight() - getPaddingBottom();
        Rect leftTitle = getViewLocByIndex(-1, 0);
        Rect topTitle = getViewLocByIndex(0, -1);
        int leftIndex = Math.max((boundLeft - leftTitle.left)/mRowTitleWidth, 0);
        int rightIndex = (int)Math.max(Math.ceil((boundRight - leftTitle.left) * 1.f / mRowTitleWidth), 0);
        int topIndex = Math.max((boundTop - topTitle.top)/mColumnTitleHeight, 0);
        int bottomIndex = (int)Math.max(Math.ceil((boundBottom - topTitle.top)*1.f/mColumnTitleHeight), 0);
        leftIndex = Math.min(Math.max(0, leftIndex), mAdapter.getColumnCount()-1);
        rightIndex = Math.min(Math.max(0, rightIndex), mAdapter.getColumnCount()-1);
        topIndex = Math.min(Math.max(0, topIndex), mAdapter.getRowCount()-1);
        bottomIndex = Math.min(Math.max(0, bottomIndex), mAdapter.getRowCount()-1);
        return new Rect(leftIndex, topIndex, rightIndex, bottomIndex);
    }

    public void stopFling() {
        mHoldFling = false;
    }
    private boolean mBounceEnable = true;
    public void disableScrollAndBounce() {
        mDisableTouch = true;
        mIsTouchScrolling = false;
        mBounceEnable = false;
    }
    public void enableTouchAndBounce() {
        mDisableTouch = false;
        mBounceEnable = true;
    }
}

