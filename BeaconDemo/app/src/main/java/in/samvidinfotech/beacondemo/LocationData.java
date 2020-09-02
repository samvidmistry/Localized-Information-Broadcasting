package in.samvidinfotech.beacondemo;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.orm.SugarRecord;
import com.orm.dsl.Table;

@Table
public class LocationData extends SugarRecord {
    Double latitude;
    Double longitude;
    String title;
    String content;
    String link;

    public LocationData() {}

    public LocationData(@NonNull String title, @NonNull String content, @Nullable String link,
                        @Nullable Double latitude,
                        @Nullable Double longitude) {
        this.title = title;
        this.content = content;
        this.link = link;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
