package com.monbureau.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class AppAdapter extends BaseAdapter {

    private final Context context;
    private final List<AppInfo> apps;

    public AppAdapter(Context context, List<AppInfo> apps) {
        this.context = context;
        this.apps = apps;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
        }

        AppInfo app = apps.get(position);

        ImageView icon = view.findViewById(R.id.app_icon);
        TextView label = view.findViewById(R.id.app_label);

        icon.setImageDrawable(app.icon);
        label.setText(app.label);

        return view;
    }
}
