package org.connectus.dagger;

import android.content.Context;
import com.squareup.picasso.Picasso;
import dagger.Module;
import dagger.Provides;
import org.connectus.EnvironmentHelper;
import org.connectus.PicassoBuilder;

import javax.inject.Singleton;

@Module
public class AppModule {

    @Provides
    @Singleton
    public Picasso providePicasso(Context context, EnvironmentHelper environmentHelper) {
        return new PicassoBuilder(context, environmentHelper).build();
    }
}
