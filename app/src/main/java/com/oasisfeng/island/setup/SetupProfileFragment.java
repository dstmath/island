package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.oasisfeng.hack.Hack;
import com.oasisfeng.island.IslandDeviceAdminReceiver;
import com.oasisfeng.island.R;
import com.oasisfeng.island.databinding.SetupProfileBinding;

import java.lang.reflect.InvocationTargetException;

import eu.chainfire.libsuperuser.Shell;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;

/**
 * This {@link Fragment} handles initiation of managed profile provisioning.
 */
public class SetupProfileFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_HAS_OTHER_PROFILE_OWNER = "has.other.owner";
    private static final String KEY_OWNER_NAME = "owner.name";
    private static final int REQUEST_PROVISION_MANAGED_PROFILE = 1;

    public static SetupProfileFragment newInstance(final boolean has_other_owner, final @Nullable String owner_name) {
        final SetupProfileFragment instance = new SetupProfileFragment();
        final Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_HAS_OTHER_PROFILE_OWNER, has_other_owner);
        if (owner_name != null) bundle.putString(KEY_OWNER_NAME, owner_name);
        instance.setArguments(bundle);
        return instance;
    }

    @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final SetupProfileBinding binding = SetupProfileBinding.inflate(inflater, container, false);
        final SetupViewModel vm = new SetupViewModel();
        final Bundle args = getArguments();
        vm.has_other_owner = args != null && args.getBoolean(KEY_HAS_OTHER_PROFILE_OWNER);
        vm.owner_name = args != null ? args.getString(KEY_OWNER_NAME) : getString(R.string.name_unknown_app);
        binding.setSetup(vm);
        return binding.getRoot();
    }

    @Override public void onViewCreated(final View view, final Bundle savedInstanceState) {
        //In setup page, ActionBar Should be hidden.
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.hide();
        view.findViewById(R.id.btn_setup_profile).setOnClickListener(this);
        View iconView = view.findViewById(R.id.img_setup_icon);
        View setupView = view.findViewById(R.id.layout_setup_content);
        setupView.setAlpha(0f);
        setupView.setScaleX(0);
        setupView.setScaleY(0);
        iconView.setScaleX(0.8f);
        iconView.setScaleY(0.8f);
        iconView.animate().setStartDelay(300L).scaleX(1.1f).scaleY(1.1f)
                .withEndAction(()->
                        iconView.animate().scaleX(1f).scaleY(1f).withEndAction(()->
                                setupView.animate()
                                .scaleX(1).scaleY(1)
                                .alpha(1)
                                .start()))
                .start();

    }


    @Override public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.btn_setup_profile: {
//                provisionDeviceOwner();
                if (! isDeviceEncrypted(getActivity()) && isEncryptionRequired())
                    provisionManagedProfileRequiringEncryption();
                else provisionManagedProfile();
                break;
            }
        }
    }

    private void provisionDeviceOwner() {
        final Activity activity = getActivity();
        if (null == activity) return;

        final Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                    .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(activity, IslandDeviceAdminReceiver.class))
                    .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
        else //noinspection deprecation
            intent = new Intent(LEGACY_ACTION_PROVISION_MANAGED_DEVICE)
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, activity.getPackageName());
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_PROVISION_MANAGED_PROFILE);
            activity.finish();
        } else Toast.makeText(activity, "Sorry, Island is not supported by your device.", Toast.LENGTH_SHORT).show();
    }
    private static final String LEGACY_ACTION_PROVISION_MANAGED_DEVICE = "com.android.managedprovisioning.ACTION_PROVISION_MANAGED_DEVICE";

    /**
     * Initiates the managed profile provisioning. If we already have a managed profile set up on
     * this device, we will get an error dialog in the following provisioning phase.
     */
    private void provisionManagedProfile() {
        final Activity activity = getActivity();
        if (null == activity) return;

        final Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            intent.putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, new ComponentName(activity, IslandDeviceAdminReceiver.class));
        else //noinspection deprecation
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, activity.getPackageName());
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_PROVISION_MANAGED_PROFILE);
            activity.finish();
        } else Toast.makeText(activity, "Sorry, Island is not supported by your device.", Toast.LENGTH_SHORT).show();
    }

    private void provisionManagedProfileRequiringEncryption() {
        new AlertDialog.Builder(getActivity()).setMessage("WARNING: Device encryption is required on your device to create the island, as a restriction enforced by Android system.\n\nFOR SUPER USER: This requirement can be avoided if root privilege is granted. Click \"NO ENC (ROOT)\" to proceed.")
                .setNegativeButton("Continue", (dialog, which) -> {
                    provisionManagedProfile();
                }).setNeutralButton("No Enc (Root)", (dialog, which) -> {
                    new AsyncTask<Void, Void, Void>() {
                        @Override protected Void doInBackground(final Void... params) {
                            try {
                                Shell.SU.run("setprop persist.sys.no_req_encrypt 1");
                            } catch (final Exception e) {
                                Log.e(TAG, "Error running root command", e);
                            }
                            return null;
                        }

                        @Override protected void onPostExecute(final Void aVoid) {
                            if (isEncryptionRequired()) provisionManagedProfileRequiringEncryption();
                            else provisionManagedProfile();
                        }
                    }.execute();
        }).setPositiveButton(android.R.string.cancel, null).show();
    }

    private static boolean isDeviceEncrypted(final Context context) {
        final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final int status = dpm.getStorageEncryptionStatus();
        return ENCRYPTION_STATUS_ACTIVE == status
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY == status);
    }

    private boolean isEncryptionRequired() {
        try {
            return !(Boolean) Hack.into("android.os.SystemProperties").method("getBoolean", String.class, boolean.class).invoke(null, "persist.sys.no_req_encrypt", false);
        } catch (Hack.HackDeclaration.HackAssertionException | InvocationTargetException e) {
            return true;
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_PROVISION_MANAGED_PROFILE) {
            if (resultCode == Activity.RESULT_OK)
                Toast.makeText(getActivity(), "Done", Toast.LENGTH_SHORT).show();
            else Toast.makeText(getActivity(), "Failed", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public SetupProfileFragment() {}

    private static final String TAG = "Island.Setup";
}
