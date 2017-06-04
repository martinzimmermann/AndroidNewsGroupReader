package com.freeteam01.androidnewsgroupreader;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.freeteam01.androidnewsgroupreader.Adapter.NewsgroupServerSpinnerAdapter;
import com.freeteam01.androidnewsgroupreader.Adapter.PostViewAdapter;
import com.freeteam01.androidnewsgroupreader.Models.NewsGroupArticle;
import com.freeteam01.androidnewsgroupreader.Models.NewsGroupEntry;
import com.freeteam01.androidnewsgroupreader.Models.NewsGroupServer;
import com.freeteam01.androidnewsgroupreader.ModelsDatabase.SubscribedNewsgroup;
import com.freeteam01.androidnewsgroupreader.Other.ISpinnableActivity;
import com.freeteam01.androidnewsgroupreader.Other.SpinnerAsyncTask;
import com.freeteam01.androidnewsgroupreader.Services.AzureService;
import com.freeteam01.androidnewsgroupreader.Services.AzureServiceEvent;
import com.freeteam01.androidnewsgroupreader.Services.RuntimeStorage;
import com.microsoft.windowsazure.mobileservices.MobileServiceActivityResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements AzureServiceEvent, ISpinnableActivity {

    private static final int REQUEST_INTERNET = 0;

    Spinner subscribed_newsgroups_spinner_;
    Spinner newsgroupsserver_spinner_;
    NewsGroupSubscribedSpinnerAdapter subscribed_spinner_adapter_;
    NewsgroupServerSpinnerAdapter server_spinner_adapter_;
    ListView post_list_view_;
    PostViewAdapter post_view_adapter_;
    ProgressBar progressBar_;
    private String selected_newsgroup_;
    private String selected_server_;
    private AtomicInteger background_jobs_count = new AtomicInteger();
    private Menu menu;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // When request completes
        if (resultCode == RESULT_OK) {
            // Check the request code matches the one we send in the login request
            if (requestCode == AzureService.LOGIN_REQUEST_CODE_GOOGLE) {
                MobileServiceActivityResult result = AzureService.getInstance().getClient().onActivityResult(data);
                if (result.isLoggedIn()) {
                    // login succeeded
                    Log.d("AzureService", "LoginActivity - login succeeded");
                    createAndShowDialog(String.format("You are now logged in - %1$2s", AzureService.getInstance().getClient().getCurrentUser().getUserId()), "Success");
                    AzureService.getInstance().OnAuthenticated();

                    Log.d("AzureService", "MainActivity - AzureService.getInstance()");
                    AzureService.getInstance().addAzureServiceEventListener(SubscribedNewsgroup.class, this);
                    Log.d("AzureService", "MainActivity subscribed to AzureEvent");
                    if (AzureService.getInstance().isAzureServiceEventFired(SubscribedNewsgroup.class)) {
                        OnLoaded(SubscribedNewsgroup.class, AzureService.getInstance().getSubscribedNewsgroups());
                        Log.d("AzureService", "MainActivity loaded entries as AzureEvent was already fired");
                    }

                    if(menu != null)
                    {
                        showOption(R.id.action_settings);
                        showOption(R.id.action_subscribe);
                        showOption(R.id.action_logout);
                        hideOption(R.id.action_login);
                    }

//                    finish();
                } else {
                    // login failed, check the error message
                    Log.d("AzureService", "LoginActivity - login failed");
                    String errorMessage = result.getErrorMessage();
                    createAndShowDialog(errorMessage, "Error");
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        Intent launch = new Intent(MainActivity.this, LoginActivity.class);
//        startActivityForResult(launch, 0);

        setContentView(R.layout.activity_main);


        newsgroupsserver_spinner_ = (Spinner) findViewById(R.id.newsgroupsserver_spinner);
        server_spinner_adapter_ = new NewsgroupServerSpinnerAdapter(this, new ArrayList<String>());
        server_spinner_adapter_.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        newsgroupsserver_spinner_.setAdapter(server_spinner_adapter_);
        progressBar_ = (ProgressBar) findViewById(R.id.progressBar);
        showNewsgroupServers();

        newsgroupsserver_spinner_.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                selected_server_ = newsgroupsserver_spinner_.getItemAtPosition(position).toString();
                showSubscribedNewsgroupsAndArticles();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selected_server_ = null;
                showSubscribedNewsgroupsAndArticles();
            }
        });

        subscribed_newsgroups_spinner_ = (Spinner) findViewById(R.id.newsgroups_spinner);
        subscribed_spinner_adapter_ = new NewsGroupSubscribedSpinnerAdapter(this, new ArrayList<String>());
        subscribed_spinner_adapter_.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        subscribed_newsgroups_spinner_.setAdapter(subscribed_spinner_adapter_);

        post_list_view_ = (ListView) findViewById(R.id.treeList);
        post_view_adapter_ = new PostViewAdapter(this, post_list_view_, this, new ArrayList<NewsGroupArticle>());
        post_list_view_.setAdapter(post_view_adapter_);

        subscribed_newsgroups_spinner_.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Log.d("AzureService", "MainActivity - onItemSelected - showNewsGroupArticles");
                selected_newsgroup_ = subscribed_newsgroups_spinner_.getSelectedItem().toString();
                Log.d("Article", "MainActivity - onItemSelected: " + selected_newsgroup_);
                showNewsGroupArticles();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.d("AzureService", "MainActivity - onNothingSelected - showNewsGroupArticles");
                selected_newsgroup_ = null;
                Log.d("Article", "MainActivity - onItemSelected: none");
                showNewsGroupArticles();
            }
        });

        if (!AzureService.isInitialized()) {
            Log.d("AzureService", "MainActivity - AzureService.Initialize(this)");
            AzureService.Initialize(this);
        }
    }

    private void showNewsGroupArticles() {
        final NewsGroupServer server = RuntimeStorage.instance().getNewsgroupServer(selected_server_);
        AsyncTask<NewsGroupServer, Void, Void> task = new SpinnerAsyncTask<NewsGroupServer, Void, Void>(this) {
            @Override
            protected Void doInBackground(NewsGroupServer... params) {
                super.doInBackground(params);
                for (NewsGroupServer server : params) {
                    try {
                        if (server == null)
                            return null;
                        server.reload();
                        server.reload(selected_newsgroup_);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                post_view_adapter_.clear();
                if (selected_server_ != null && selected_newsgroup_ != null) {
                    NewsGroupEntry ng = RuntimeStorage.instance().getNewsgroupServer(selected_server_).getNewsgroup(selected_newsgroup_);
                    post_view_adapter_.addAll(ng.getArticles());
                }
                post_view_adapter_.notifyDataSetChanged();
                super.onPostExecute(aVoid);
            }
        };
        task.execute(server);
    }

    private void showNewsgroupServers() {
        server_spinner_adapter_.clear();
        server_spinner_adapter_.addAll(RuntimeStorage.instance().getAllNewsgroupServers());
        server_spinner_adapter_.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    private void disableOption(int id) {
        MenuItem item = menu.findItem(id);
        item.setEnabled(false);
    }

    private void enableOption(int id) {
        MenuItem item = menu.findItem(id);
        item.setEnabled(true);
    }

    private void hideOption(int id) {
        MenuItem item = menu.findItem(id);
        item.setVisible(false);
    }

    private void showOption(int id) {
        MenuItem item = menu.findItem(id);
        item.setVisible(true);
    }

    private void setOptionTitle(int id, String title) {
        MenuItem item = menu.findItem(id);
        item.setTitle(title);
    }

    private void setOptionIcon(int id, int iconRes) {
        MenuItem item = menu.findItem(id);
        item.setIcon(iconRes);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.action_subscribe:
                Intent launch = new Intent(MainActivity.this, SubscribeActivity.class);
                startActivityForResult(launch, 0);
                return true;
            case R.id.action_logout:
                AzureService.getInstance().logout();
                hideOption(R.id.action_settings);
                hideOption(R.id.action_subscribe);
                hideOption(R.id.action_logout);
                showOption(R.id.action_login);
                createAndShowDialog("Successfully logged out", "Success");
                return true;
            case R.id.action_login:
                AzureService.getInstance().authenticate();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void addedBackgroundJob() {
        background_jobs_count.getAndIncrement();
        setSpinnerVisibility();
    }

    @Override
    public void finishedBackgroundJob() {
        background_jobs_count.getAndDecrement();
        setSpinnerVisibility();
    }

    void setSpinnerVisibility() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (background_jobs_count.get() == 0) {
                    progressBar_.setVisibility(View.GONE);
                } else {
                    progressBar_.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public <T> void OnLoaded(Class<T> classType, List<T> entries) {
        Log.d("AzureService", "MainActivity.OnLoaded: " + classType.getSimpleName());
        if (classType == SubscribedNewsgroup.class) {
            showSubscribedNewsgroupsAndArticles();
        }
    }

    private void showSubscribedNewsgroupsAndArticles() {
        final NewsGroupServer server = RuntimeStorage.instance().getNewsgroupServer(selected_server_);
        final TreeSet<String> subscribedNewsGroupEntries;
        if (server != null)
            subscribedNewsGroupEntries = server.getSubscribed();
        else
            subscribedNewsGroupEntries = null;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String copy = selected_newsgroup_;
                Log.d("Article", "MainActivity - selected_newsgroup_runOnUiThread - before: " + selected_newsgroup_);
                subscribed_spinner_adapter_.clear();
                if (subscribedNewsGroupEntries != null)
                    subscribed_spinner_adapter_.addAll(subscribedNewsGroupEntries);
                subscribed_spinner_adapter_.notifyDataSetChanged();

                if (subscribed_newsgroups_spinner_.getSelectedItem() == null)
                    selected_newsgroup_ = null;
                else
                    selected_newsgroup_ = subscribed_newsgroups_spinner_.getSelectedItem().toString();

                if (subscribedNewsGroupEntries != null) {
                    if (!subscribedNewsGroupEntries.isEmpty() && (copy != null && !subscribedNewsGroupEntries.contains(copy))) {
//                        subscribed_newsgroups_spinner_.setSelection(Adapter.NO_SELECTION);
//                        subscribed_newsgroups_spinner_.setSelection(0);
//                        selected_newsgroup_ = subscribed_newsgroups_spinner_.getSelectedItem().toString();
                        showNewsGroupArticles();
                        Log.d("Article", "MainActivity - set selected newsgroup to " + selected_newsgroup_);
                    } else if (copy == null || !subscribedNewsGroupEntries.contains(copy)) {
//                        subscribed_newsgroups_spinner_.setSelection(Adapter.NO_SELECTION);
//                        selected_newsgroup_ = null;
                        showNewsGroupArticles();
                        Log.d("Article", "MainActivity - set selected newsgroup to none");
                    }
                }
                Log.d("Article", "MainActivity - selected_newsgroup_runOnUiThread - after: " + selected_newsgroup_);
            }
        });
    }

    public class NewsGroupSubscribedSpinnerAdapter extends ArrayAdapter<String> {
        public NewsGroupSubscribedSpinnerAdapter(Context context, ArrayList<String> newsgroups) {
            super(context, 0, newsgroups);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            String newsgroup = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.newsgroup_post_listview, parent, false);
            }

            TextView tv_name = (TextView) convertView.findViewById(R.id.tv_post);
            tv_name.setText(newsgroup);
            return convertView;
        }
    }

    @Override
    protected void onResume() {
        showNewsGroupArticles();
        showNewsgroupServers();
        super.onResume();
    }

    private void createAndShowDialog(Exception exception, String title) {
        Throwable ex = exception;
        if (exception.getCause() != null) {
            ex = exception.getCause();
        }
        createAndShowDialog(ex.getMessage(), title);
    }

    private void createAndShowDialog(final String message, final String title) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage(message);
        builder.setTitle(title);
        builder.create().show();
    }
}
