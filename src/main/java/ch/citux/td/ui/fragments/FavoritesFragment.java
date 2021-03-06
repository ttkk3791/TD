/*
 * Copyright 2013-2014 Paul Stöhr
 *
 * This file is part of TD.
 *
 * TD is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.citux.td.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;

import org.apache.commons.lang3.StringUtils;

import ch.citux.td.R;
import ch.citux.td.config.TDConfig;
import ch.citux.td.data.model.TwitchChannel;
import ch.citux.td.data.model.TwitchFollows;
import ch.citux.td.data.model.TwitchStream;
import ch.citux.td.data.service.TDServiceImpl;
import ch.citux.td.data.worker.TDBasicCallback;
import ch.citux.td.data.worker.TDTaskManager;
import ch.citux.td.ui.adapter.FavoritesAdapter;
import ch.citux.td.ui.widget.EmptyView;
import ch.citux.td.ui.widget.ListView;

public class FavoritesFragment extends TDListFragment<TwitchFollows> implements AdapterView.OnItemClickListener, ListView.OnLastItemVisibleListener {

    private String channelName;
    private SharedPreferences preferences;
    private FavoritesAdapter adapter;
    private int offset;
    private int total;

    @Override
    protected int onCreateView() {
        return R.layout.list;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (adapter == null || adapter.getCount() == 0) {
            adapter = new FavoritesAdapter(getActivity());

            setListAdapter(adapter);
            loadData();
        }

        if (!hasUsername) {
            EmptyView emptyView = (EmptyView) getListView().getEmptyView();
            if (emptyView != null) {
                emptyView.setText(R.string.channel_name_empty);
            }
        }
        getListView().setOnItemClickListener(this);
        getListView().setOnLastItemVisibleListener(this);
    }

    @Override
    public void loadData() {
        channelName = preferences.getString(TDConfig.SETTINGS_CHANNEL_NAME, "");
        if (StringUtils.isNotBlank(channelName)) {
            TDTaskManager.executeTask(this);
        }
    }

    @Override
    public void refreshData() {
        if (!channelName.equals(preferences.getString(TDConfig.SETTINGS_CHANNEL_NAME, channelName))) {
            //Channel change, reload favorites
            adapter.clear();
            loadData();
        } else {
            //Only status update
            for (int i = 0; i < adapter.getData().size(); i++) {
                TwitchChannel channel = adapter.getData().valueAt(i);
                adapter.setUpdatePending(channel.get_id());
                TDTaskManager.executeTask(new StatusCallback(this, channel));
            }
        }
    }

    @Override
    public TwitchFollows startRequest() {
        return TDServiceImpl.getInstance().getFollows(channelName.trim(), offset);
    }

    @Override
    public void onResponse(TwitchFollows response) {
        if (adapter == null) {
            adapter = new FavoritesAdapter(getActivity(), response.getFollows());
            setListAdapter(adapter);
        } else {
            adapter.setData(response.getFollows());
        }

        if (response.getFollows() != null) {
            for (int i = 0; i < response.getFollows().size(); i++) {
                TwitchChannel channel = response.getFollows().valueAt(i);
                TDTaskManager.executeTask(new StatusCallback(this, channel));
            }
        }
        total = response.get_total();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TwitchChannel channel = adapter.getItem(position);
        if (channel != null && channel.getChannelStatus() != TwitchChannel.Status.UNKNOWN) {
            getTDActivity().showChannel(channel);
        }
    }

    @Override
    public void onLastItemVisible() {
        offset += 25;
        if (offset < total) {
            loadData();
        }
    }

    private class StatusCallback extends TDBasicCallback<TwitchStream> {

        private TwitchChannel channel;

        protected StatusCallback(Object caller, TwitchChannel channel) {
            super(caller);
            this.channel = channel;
        }

        @Override
        public TwitchStream startRequest() {
            return TDServiceImpl.getInstance().getStream(channel.getName());
        }

        @Override
        public void onResponse(TwitchStream response) {
            adapter.updateChannelStatus(channel.get_id(), response.getStream() != null);
        }

        @Override
        public boolean isAdded() {
            return FavoritesFragment.this.isAdded();
        }
    }
}
