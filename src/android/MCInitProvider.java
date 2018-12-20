/**
 * Copyright 2018 Salesforce, Inc
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.marketingcloud.cordova;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.salesforce.marketingcloud.InitializationStatus;
import com.salesforce.marketingcloud.MarketingCloudConfig;
import com.salesforce.marketingcloud.MarketingCloudSdk;
import com.salesforce.marketingcloud.registration.RegistrationManager;
import java.util.Set;
import com.salesforce.marketingcloud.notifications.NotificationCustomizationOptions;
import com.salesforce.marketingcloud.notifications.NotificationManager;
import com.salesforce.marketingcloud.notifications.NotificationMessage;
import com.amrest.latagliatella.R.drawable;
import com.amrest.latagliatella.R;
import android.support.v4.app.NotificationCompat;
import android.graphics.Color;


public class MCInitProvider extends ContentProvider
    implements MarketingCloudSdk.InitializationListener {

  @Override public boolean onCreate() {
    Context ctx = getContext();
    if (ctx != null) {
      MarketingCloudConfig.Builder builder = MCSdkConfig.prepareConfigBuilder(ctx);
      if (builder != null) {
        MarketingCloudSdk.init(ctx, builder
        .setNotificationCustomizationOptions(
          NotificationCustomizationOptions.create(new NotificationManager.NotificationBuilder() {
            @NonNull @Override
            public NotificationCompat.Builder setupNotificationBuilder(@NonNull Context context,
                @NonNull NotificationMessage notificationMessage) {
              NotificationCompat.Builder builder =
                  NotificationManager.getDefaultNotificationBuilder(
                      context,
                      notificationMessage,
                      NotificationManager.createDefaultNotificationChannel(context),
                      R.drawable.ic_notification
                  );

                //builder.setColor(0xffff0000);
                builder.setLights(0xffff0000, 500, 2000);
                builder.setColor(Color.parseColor("#8c1713"));
                
              return builder;
            }
          })
        )
        .build(ctx), this);
      }
    }
    return false;
  }

  @Nullable @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
      @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    return null;
  }

  @Nullable @Override public String getType(@NonNull Uri uri) {
    return null;
  }

  @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    return null;
  }

  @Override public int delete(@NonNull Uri uri, @Nullable String selection,
      @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
      @Nullable String[] selectionArgs) {
    return 0;
  }

  @Override public void complete(@NonNull InitializationStatus status) {
    if (status.isUsable()) {
      MarketingCloudSdk.requestSdk(new MarketingCloudSdk.WhenReadyListener() {
        @Override public void ready(@NonNull MarketingCloudSdk marketingCloudSdk) {
          RegistrationManager registrationManager = marketingCloudSdk.getRegistrationManager();
          registrationManager.edit().addTag("Cordova").commit();
        }
      });
    }
  }

}
