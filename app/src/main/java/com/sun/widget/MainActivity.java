package com.sun.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends Activity {


    private ViewGroup mRootView = null;
    private CrossList mList = null;

    private Pair<Integer, Integer> mSelected = new Pair<Integer, Integer>(-1, -1);
    private ArrayList<Pair<Integer, Integer>> mRefreshList = null;

    private CrossListAdapter mListAdapter = new CrossListAdapter() {
        @Override
        public int getRowCount() {
            return 30;
        }

        @Override
        public int getColumnCount() {
            return 30;
        }

        @Override
        public View getTableHeaderView() {
            LayoutInflater inflater = (LayoutInflater)MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ViewGroup tableTitle = (ViewGroup)inflater.inflate(R.layout.flight_cross_calendar_table_title_for_global, null);
            View backgroudLine = new View(MainActivity.this){
                @Override
                public void draw(Canvas canvas) {
                    super.draw(canvas);
                    Paint paint = new Paint();
                    paint.setColor(0xffe4e4e9);
                    canvas.drawLine(0, 0, this.getMeasuredWidth(), this.getMeasuredHeight(), paint);
                }
            };
            backgroudLine.setBackgroundColor(0x00000000);
            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            tableTitle.addView(backgroudLine, 0, layoutParams);

//            //rotate
//            View divideLine = tableTitle.findViewById(R.id.divide_line);
//            RotateAnimation anim = new RotateAnimation(40, 40, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
//            anim.setDuration(100);
//            anim.setFillAfter(true);
//            divideLine.startAnimation(anim);

            return tableTitle;
        }

        @Override
        public View getRowTitleView(int pos, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater)MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.flight_cross_calendar_row_title_for_global, null);
                holder = new ViewHolder();
                holder.desc = (TextView)convertView.findViewById(R.id.week_day);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            holder.desc.setText(pos + "");
            return convertView;
        }

        @Override
        public View getColumnTitleView(int pos, View convertView, ViewGroup parent) {
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater)MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.flight_cross_calendar_column_title_for_global, null);
            }
            TextView text = (TextView)convertView.findViewById(R.id.week_day);
            text.setText(pos + "");
            return convertView;
        }

        @Override
        public View getContentView(int row, int col, View convertView, ViewGroup parent) {
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater)MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.flight_cross_calendar_content_for_global, null);
            }
            TextView text = (TextView)convertView.findViewById(R.id.price);
            text.setText((row + col) + "");
            return convertView;
        }

        @Override
        public Object getRowTitleItem(int pos) {
            return null;
        }

        @Override
        public Object getColumnTitleItem(int pos) {
            return null;
        }

        @Override
        public Object getContentItem(int row, int col) {
            return null;
        }

        class ViewHolder{
            TextView price;
            TextView desc;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = (ViewGroup)inflater.inflate(R.layout.activity_main, null);
        setContentView(mRootView);
        addCrossList();
    }

    public void addCrossList() {
        if(mList != null) {
            return ;
        }
        mList = (CrossList)mRootView.findViewById(R.id.cross_list);
        mList.setAdapter(mListAdapter);
        mList.setItemClickListener(new CrossList.OnItemClickListener(){

            @Override
            public void onRowTitleClicked(CrossList listView, int index) {
                Toast.makeText(MainActivity.this, index + "", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onColumnTitleClicked(CrossList listView, int index) {
                Toast.makeText(MainActivity.this, index+30+"", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onContentItemClicked(CrossList listView, int row, int col) {
                mSelected = new Pair(row, col);
                mListAdapter.notifyDataSetInvalidated(mRefreshList);
                ArrayList<Pair<Integer, Integer>> newRefreshList = new ArrayList<Pair<Integer, Integer>>();
                for(int r=-1;r<=row;r++) {
                    newRefreshList.add(new Pair(r, col));
                }
                for(int c=-1;c<col;c++) {
                    newRefreshList.add(new Pair(row, c));
                }
                mListAdapter.notifyDataSetInvalidated(newRefreshList);
                mRefreshList = newRefreshList;
            }
        });
        if(mSelected != null) {
            mList.scrollTo(mSelected.first, mSelected.second);
        }
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        LayoutInflater inflater = getLayoutInflater();
        mList.setOverScrollView(inflater.inflate(R.layout.flight_cross_calendar_over_scroll_left, null),  CrossList.OVER_SCROLL_LEFT);
        lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mList.setOverScrollView(inflater.inflate(R.layout.flight_cross_calendar_over_scroll_top, null), CrossList.OVER_SCROLL_TOP);
        mList.setOverScrollListener(mOverScroll);
    }


    private CrossList.OverScrollListener mOverScroll = new CrossList.OverScrollListener() {
        @Override
        public void onScrollOverBy(CrossList list, ArrayList<CrossList.OverScrollModel> overList) {
            for(CrossList.OverScrollModel overModel : overList) {
                TextView text = (TextView)overModel.overView.findViewById(R.id.over_scroll_text);
                if(overModel.overDegree > 1.0f) {
                    text.setText("松开刷新");
                } else {
                    text.setText("拖拽刷新");
                }
            }
        }

        @Override
        public void onScrollOverRelease(final CrossList list, ArrayList<CrossList.OverScrollModel> overList) {
            int triggerType = -1;
            for(CrossList.OverScrollModel overModel : overList) {
                if(overModel.overDegree > 1.0f) {
                    triggerType = overModel.type;
                    break;
                }
            }
            if(triggerType == -1) {
                return ;
            }
            for(CrossList.OverScrollModel overModel : overList) {
                if(overModel.type == triggerType) {
                    list.disableScrollAndBounce();
                    list.stopFling();
                    final TextView text = (TextView)overModel.overView.findViewById(R.id.over_scroll_text);
                    final View progressBar = overModel.overView.findViewById(R.id.progress_bar);
                    list.showOverScrollView(triggerType);
                    final int curType = triggerType;
                    text.setText("加载中");
                    progressBar.setVisibility(View.VISIBLE);
                    list.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            text.setText("刷新完成");
                            progressBar.setVisibility(View.GONE);
                            list.hideOverScrollView(curType);
                            list.enableTouchAndBounce();
                        }
                    }, 1000);
                } else {
                    list.hideOverScrollView(overModel.type);
                }
            }
        }
    };

    public void removeCrossList() {
        if(mList == null) {
            return ;
        }
        mRootView.removeView(mList);
        mList.setAdapter(null);
        mList = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return super.onOptionsItemSelected(item);
    }
}
