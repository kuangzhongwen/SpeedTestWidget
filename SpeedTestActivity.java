package com.zlianjie.coolwifi.speedtest;

import com.zlianjie.coolwifi.R;
import com.zlianjie.coolwifi.SingleFragmentActivity;

/**
 * 测速
 * @author lisen
 * @since 2015-07-08
 */
public class SpeedTestActivity extends SingleFragmentActivity<SpeedTestFragment> {

    @Override
    protected SpeedTestFragment initFragment() {
        return SpeedTestFragment.newInstance(R.id.content, getIntent().getExtras());
    }
}
