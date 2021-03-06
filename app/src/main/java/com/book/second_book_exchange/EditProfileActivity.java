package com.book.second_book_exchange;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.book.second_book_exchange.api.ApiTool;
import com.book.second_book_exchange.log.JoyceLog;
import com.book.second_book_exchange.tool.ImageLoaderProvider;
import com.book.second_book_exchange.tool.LoadingDialog;
import com.book.second_book_exchange.tool.StorageTool;
import com.book.second_book_exchange.tool.ViewDialog;
import com.book.second_book_exchange.widget.GlideEngine;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;
import com.book.second_book_exchange.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView ivBack, ivClear, ivSave, ivUploadPic, ivUploadPicIcon;
    private EditText edAccount, edNickname, edTel, edEmail;
    private TextInputLayout tilNickName, tilTel;
    private FirebaseFirestore db;
    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private UserBasicData userBasicData;
    private Bitmap bitmap;
    private byte[] bytes;
    private LoadingDialog loadingDialog;
    private CompositeDisposable compositeDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        compositeDisposable = new CompositeDisposable();
        userBasicData = new UserBasicData();

        initView();

        //????????????????????????
        getMemberInfo();

        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        ivClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAlertDialog("??????????????????????????????");
            }
        });

        ivSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //??????????????????,????????????
                if (bitmap == null && userBasicData.getUserPhotoUrl()==null) {
                    showHint("???????????????");
                    return;
                }

                //??????????????????
                if (bitmap != null){
                    startUploadPic(bitmap);
                    return;
                }

                //???????????????????????????,???????????????????????????
                if (userBasicData.getUserPhotoUrl() != null) {
                    uploadPersonalData(userBasicData.getUserPhotoUrl());
                    return;
                }
            }
        });

        ivUploadPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //PictureSelector??????
                PictureSelector.create(EditProfileActivity.this)
                        .openGallery(SelectMimeType.ofImage())
                        .setMaxSelectNum(1)
                        .setMinSelectNum(1)
                        .setImageEngine(GlideEngine.createGlideEngine())
                        .forResult(new OnResultCallbackListener<LocalMedia>() {
                            @Override
                            public void onResult(ArrayList<LocalMedia> result) {

                                String localMedia = result.get(0).getPath();
                                Uri uri = Uri.parse(localMedia);

                                try {
                                    //????????????
                                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),uri);

                                    //????????????
                                    ivUploadPicIcon.setVisibility(View.GONE);
                                    ivUploadPic.setImageBitmap(bitmap);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onCancel() {
                            }
                        });
            }
        });
    }

    private void startUploadPic(Bitmap bitmap) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 20;
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        bytes = baos.toByteArray();

        showProgressDialog();
        uploadPicToStorage(bytes);

    }

    private void uploadPicToStorage(byte[] bytes) {
        StorageTool.uploadBookListPhoto(bytes, new StorageTool.OnUploadPhotoResultListener() {
            @Override
            public void onSuccess(String userPhotoUrl) {

                Log.i("Joyce", "EditProfile --> uploadPicToStorage --> ??????????????????");
                loadingDialog.dismiss();
                uploadPersonalData(userPhotoUrl);
            }

            @Override
            public void onFail(String error) {

                Log.i("Joyce", "EditProfile --> uploadPicToStorage --> ??????????????????");
                loadingDialog.dismiss();
                showHint("??????????????????");
            }
        });
    }

    private void uploadPersonalData(String userPhotoUrl) {


        //???????????????????????????
        String nickName = edNickname.getText().toString();
        String tel = edTel.getText().toString();

        if (isCheckNewData(nickName, tel)) {
            return;
        }

        userBasicData.setNickName(nickName);
        JoyceLog.i("EditProfileActivity | setNickName: "+nickName);

        userBasicData.setTel(tel);
        JoyceLog.i("EditProfileActivity | setTel: "+tel);

        userBasicData.setUserPhotoUrl(userPhotoUrl);
        JoyceLog.i("EditProfileActivity | ?????????PhotoUrl: " + userPhotoUrl);

        JoyceLog.i("EditProfileActivity | userBasicData: "+new Gson().toJson(userBasicData));

        ApiTool.getRequestApi()
                .editUserData(userBasicData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<UserBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull UserBasicData userBasicData) {
                        showHint("????????????????????????");
                        finish();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("EditProfileActivity | editUserData | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("EditProfileActivity | editUserData | onComplete");
                    }
                });
    }

    private void showHint(String content) {
        Toast.makeText(EditProfileActivity.this, content, Toast.LENGTH_SHORT).show();
    }

    private void showProgressDialog() {

        loadingDialog = LoadingDialog.newInstance();
        loadingDialog.show(getSupportFragmentManager(), "dialog");

        Log.i("Joyce", "EditProfile --> showProgressDiaLog() --> show Dialog");

    }

    private boolean isCheckNewData(String nickName, String tel) {
        boolean isNeedToWriteData = false;

        if (nickName.isEmpty() && tel.isEmpty()) {
            tilNickName.setError("???????????????");
            tilTel.setError("???????????????");
            return true;
        }

        if (TextUtils.isEmpty(nickName)) {
            tilNickName.setError("???????????????");
            isNeedToWriteData = true;
        } else if (nickName.length() > tilNickName.getCounterMaxLength() || nickName.length() < 1) {
            tilNickName.setError("?????????????????????1?????????");
            isNeedToWriteData = true;
        } else {
            tilNickName.setError(null);
        }

        if (TextUtils.isEmpty(tel)) {
            tilTel.setError("???????????????");
            isNeedToWriteData = true;
        } else if (tel.length() != 10) {
            tilTel.setError("????????????????????????");
            isNeedToWriteData = true;
        } else {
            tilTel.setError(null);
        }

        return isNeedToWriteData;
    }

    private void showAlertDialog(String content) {
        ViewDialog alert = new ViewDialog();
        alert.showDialog(EditProfileActivity.this, content);
        alert.setOnAlertDialogClickListener(new ViewDialog.OnAlertDialogClickListener() {
            @Override
            public void onConfirm() {
                edNickname.setText("");
                edTel.setText("");
            }

            @Override
            public void onCancel() {

            }
        });
    }

    private void getMemberInfo() {

        userBasicData.setUserUid(user.getUid());
        userBasicData.setEmail(user.getEmail());

        ApiTool.getRequestApi()
                .checkUserData(userBasicData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<UserBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull UserBasicData userData) {

                        JoyceLog.i("EditProfileActivity | userData: "+new Gson().toJson(userData));
                        userBasicData = userData;

                        edAccount.setText(userData.getEmail());
                        edEmail.setText(userData.getEmail());
                        edNickname.setText(userData.getNickName());
                        edTel.setText(userData.getTel());
                        ImageLoaderProvider.getInstance().setImage(userData.getUserPhotoUrl(), ivUploadPic);

                        if (userData.getUserPhotoUrl() != null) {
                            ivUploadPicIcon.setVisibility(View.GONE);
                            Log.i("Joyce", "EditProfileActivity | ?????????PhotoUrl: " + userData.getUserPhotoUrl());
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("EditProfileActivity | checkUserData | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("EditProfileActivity | checkUserData | onComplete");
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    private void initView() {

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        ivBack = findViewById(R.id.back);
        ivClear = findViewById(R.id.clear);
        ivSave = findViewById(R.id.save);
        ivUploadPic = findViewById(R.id.upload_picture);
        ivUploadPicIcon = findViewById(R.id.upload_add);

        edAccount = findViewById(R.id.ed_account);
        edNickname = findViewById(R.id.ed_nickname);
        edTel = findViewById(R.id.ed_tel);
        edEmail = findViewById(R.id.ed_email);

        tilNickName = findViewById(R.id.lo_nickname);
        tilTel = findViewById(R.id.lo_tel);
    }
}