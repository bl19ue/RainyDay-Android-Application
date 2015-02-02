package com.my.sumit.rainyday;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.text.*;

public class ForecastFragment extends Fragment {

    ArrayAdapter mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        //To handle menu items that we added, we need to set:
        setHasOptionsMenu(true);
        //After this onCreateOptionMenu is called
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
            fetchWeatherTask.execute();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ArrayList<String> weekForecast = new ArrayList<String>();
        weekForecast.add("Today-rainy-12/7");
        weekForecast.add("Tomorrow-rainy-14/4");
        weekForecast.add("Wed-sunny-24/12");
        weekForecast.add("Thurs-cloudy-12/7");
        weekForecast.add("Fri-sunny-12/7");
        weekForecast.add("Sat-rainy-12/7");
        weekForecast.add("Sun-rainy-12/7");

        mForecastAdapter = new ArrayAdapter<String>(
                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textview,
                weekForecast);
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //Toast.makeText(getActivity(), mForecastAdapter.getItem(position).toString(), Toast.LENGTH_LONG).show();
                String forecast = mForecastAdapter.getItem(position).toString();
                //Now we will start the detail Activity when any item is clicked
                // Executed in an Activity, so 'this' is the Context
                // The fileUrl is a string URL, such as "http://www.example.com/image.png"
                Intent detailIntent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(detailIntent);
            }
        });

        return rootView;
    }



    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.

            Date date = new Date(time * 1000);

            SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
            return format.format(date).toString();
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * <p/>
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws Exception {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DATETIME = "dt";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime = dayForecast.getLong(OWM_DATETIME);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {
            //Using openweather api to get realtime data for forecasting

            //Declaring these two outside of try catch, as they need to be closed after
            //we are done with them in the final block

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            //This will contain raw JSON response as a string
            String forecastJsonStr = null;

            try {
                //This is the constructed URL for the OpenWeatherMap query
                //Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast

                //Daily - upto 7 days, zip - 95110, format - JSON, units - metric
                URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=San%20Jose,ca&mode=json&units=metric&cnt=7");

                //Create a request to openweathermap and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                //Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();

                if (inputStream == null) {
                    //Nothing to do
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    //As its a JSON String, we dont need to add a newline,
                    //But if we add it, it would make debugging a lot easier
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    //Buffer was itself empty, no need to parse it..
                    return null;
                }
                forecastJsonStr = buffer.toString();
                //String weatherList[] = getWeatherDataFromJson(forecastJsonStr, 7);
                //System.out.println("w="+weatherList[1]);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error ooh", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                        return getWeatherDataFromJson(forecastJsonStr, 7);

                    } catch (final Exception e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

        return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if(result != null){
                mForecastAdapter.clear();
                for(String day_forecast : result){
                    mForecastAdapter.add(day_forecast);
                }
            }
        }
    }


        /* The date/time conversion code is going to be moved outside the asynctask later,
 * so for convenience we're breaking it out into its own method now.
 */


}