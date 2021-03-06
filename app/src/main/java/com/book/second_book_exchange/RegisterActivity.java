package com.book.second_book_exchange;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.book.second_book_exchange.api.ApiTool;
import com.book.second_book_exchange.log.JoyceLog;
import com.book.second_book_exchange.tool.LoadingDialog;
import com.book.second_book_exchange.tool.StorageTool;
import com.book.second_book_exchange.tool.ViewDialog;
import com.book.second_book_exchange.widget.GlideEngine;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
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

public class RegisterActivity extends AppCompatActivity {

    private ImageView ivBack, ivClear, ivSubmit, ivUploadPic, ivUploadPicIcon;
    private EditText edAccount, edPassword, edNickName, edTel, edEmail;
    private TextInputLayout tilAccount, tilPassword, tilNickName, tilTel, tilEmail;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Bitmap bitmap;
    private byte[] bytes;
    private LoadingDialog loadingDialog;
    private CompositeDisposable compositeDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        JoyceLog.i("start add new book");

        compositeDisposable = new CompositeDisposable();
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initView();

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

        ivSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (bitmap == null){
                    showHint("???????????????");
                    return;
                }

                startToUploadPic(bitmap);
            }
        });

        ivUploadPic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //PictureSelector??????
                PictureSelector.create(RegisterActivity.this)
                        .openGallery(SelectMimeType.ofImage())
                        .setMaxSelectNum(1)
                        .setMinSelectNum(1)
                        .setImageEngine(GlideEngine.createGlideEngine())
                        .forResult(new OnResultCallbackListener<LocalMedia>() {
                            @Override
                            public void onResult(ArrayList<LocalMedia> result) {

                                String localMedia = result.get(0).getPath();
                                JoyceLog.i("localMedia: "+result.get(0).getPath());

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
                
//                ??????????????????,??????onActivityResult
//                CropImage.activity().setGuidelines(CropImageView.Guidelines.ON)
//                        .start(RegisterActivity.this);
            }
        });

        edAccount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

                edEmail.setText(edAccount.getText().toString());
            }
        });
    }

    private void startToUploadPic(Bitmap bitmap) {

        //????????????,??????byte??????
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 20;
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        bytes = baos.toByteArray();

        //??????Loading Dialog
        showProgressDialog();

        //????????????
        uploadPicToStorage(bytes);
    }

    private void uploadPicToStorage(byte[] bytes) {

        StorageTool.uploadBookListPhoto(bytes, new StorageTool.OnUploadPhotoResultListener() {
            @Override
            public void onSuccess(String userPhotoUrl) {

                Log.i("Joyce","Register --> uploadPicToStorage --> ??????????????????");
                loadingDialog.dismiss();

                uploadPersonalData(userPhotoUrl);
            }

            @Override
            public void onFail(String error) {

                Log.i("Joyce","Register --> uploadPicToStorage --> ??????????????????");
                loadingDialog.dismiss();
                showHint("??????????????????");
            }
        });
    }

    private void uploadPersonalData(String userPhotoUrl) {

        String account = edAccount.getText().toString();
        String password = edPassword.getText().toString();
        String nickName = edNickName.getText().toString();
        String tel = edTel.getText().toString();
        String email= edEmail.getText().toString();

        if (isCheckNewData(account, password, nickName, tel, email)) {
            return;
        }

        //??????????????????email
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.i("Joyce", "????????????");
                            FirebaseUser user = mAuth.getCurrentUser();

                            //???????????????,??????????????????????????????
                            updateUserBasicData(user, userPhotoUrl);
                            updateUI();

                        } else {
                            Log.i("Joyce", "failed + " + task.getException());
                            showHint("????????????");
                        }
                    }
                });
    }

    private void showProgressDialog() {

        loadingDialog = LoadingDialog.newInstance();
        loadingDialog.show(getSupportFragmentManager(),"dialog");

        Log.i("Joyce","Register --> showProgressDiaLog() --> show Dialog");

    }

//    ????????????data??????,????????????
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK){
//            CropImage.ActivityResult result = CropImage.getActivityResult(data);
//
//            if (result == null){
//                Log.i("Joyce","???????????????");
//                return;
//            }
//
//            try {
//
//                //????????????
//                Uri uri = result.getUri();
//                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
//
//                //????????????
//                ivUploadPicIcon.setVisibility(View.GONE);
//                ivUploadPic.setImageBitmap(bitmap);
//
//            }catch (Exception e ){
//                e.printStackTrace();
//            }
//        }
//    }

    private void updateUserBasicData(FirebaseUser user, String userPhotoUrl) {

        UserBasicData userBasicData = new UserBasicData();

        userBasicData.setUserUid(user.getUid());
        userBasicData.setEmail(user.getEmail());
        userBasicData.setNickName(edNickName.getText().toString());
        userBasicData.setTel(edTel.getText().toString());
        userBasicData.setUserPhotoUrl(userPhotoUrl);

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
                    public void onNext(@NonNull UserBasicData userBasicData) {
                        updateUI();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("RegisterActivity | checkUserData | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("RegisterActivity | checkUserData | onComplete");
                    }
                });
    }

    private void updateUI() {
        finish();
    }

    private boolean isCheckNewData(String account, String password, String nickName, String tel, String email) {

        //??????????????????????????????????????????????????????????????????????????? ????????????????????????????????? ???????????????True
        boolean isNeedToWriteData = false;

        //?????????????????????????????????,????????????
        if (account.isEmpty() && password.isEmpty() && nickName.isEmpty() && tel.isEmpty() && email.isEmpty()) {

            tilAccount.setError("???????????????");
            tilPassword.setError("???????????????");
            tilNickName.setError("?????????????????????");
            tilTel.setError("???????????????");
            tilEmail.setError("?????????????????????");
            return true;
        }

        //???????????????????????????(????????????)??????????????????????????????????????????????????? ???????????????????????????
        if (TextUtils.isEmpty(account)) {
            tilAccount.setError("???????????????");
            isNeedToWriteData = true;
        } else if (!isEmailValid(account)) {
            isNeedToWriteData = true;
            tilAccount.setError("??????????????????Email");
        } else {
            tilAccount.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("???????????????");
            isNeedToWriteData = true;
        } else if (password.length() > tilPassword.getCounterMaxLength() || password.length() < 6) {
            tilPassword.setError("????????????6~8?????????");
            isNeedToWriteData = true;
        } else if (!isPasswordUpperCase(password)) {
            tilPassword.setError("?????????????????????????????????");
            isNeedToWriteData = true;
        } else {
            Log.i("Joyce", "????????????6~8????????? null");
            tilPassword.setError(null);
        }


        if (TextUtils.isEmpty(nickName)) {
            tilNickName.setError("?????????????????????");
            isNeedToWriteData = true;
        }else if (nickName.length() > tilNickName.getCounterMaxLength() || nickName.length() < 1) {
            tilNickName.setError("?????????????????????1?????????");
            isNeedToWriteData = true;
        } else {
            tilNickName.setError(null);
        }

        if (TextUtils.isEmpty(tel)) {
            tilTel.setError("???????????????");
            isNeedToWriteData = true;
        }else if (tel.length() != 10) {
            tilTel.setError("????????????????????????");
            isNeedToWriteData = true;
        } else {
            tilTel.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("?????????Email");
            isNeedToWriteData = true;
        } else if (!isEmailValid(email)) {
            tilEmail.setError("Email????????????");
            isNeedToWriteData = true;
        } else {
            tilEmail.setError(null);
        }

        return isNeedToWriteData;
    }

    private boolean isPasswordUpperCase(String password) {
        return Character.isUpperCase(password.charAt(0));
    }

    private boolean isEmailValid(CharSequence email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showAlertDialog(String content) {
        ViewDialog alert = new ViewDialog();
        alert.showDialog(RegisterActivity.this, content);
        alert.setOnAlertDialogClickListener(new ViewDialog.OnAlertDialogClickListener() {
            @Override
            public void onConfirm() {
                clearEd();
            }

            @Override
            public void onCancel() {

            }
        });
    }

    private void showHint(String content){
        Toast.makeText(RegisterActivity.this,content,Toast.LENGTH_SHORT).show();
    }

    private void clearEd() {
        edAccount.setText("");
        edPassword.setText("");
        edNickName.setText("");
        edTel.setText("");
        edEmail.setText("");

        tilAccount.setError(null);
        tilPassword.setError(null);
        tilNickName.setError(null);
        tilTel.setError(null);
        tilEmail.setError(null);
    }

    private void initView() {

        JoyceLog.i("initView");

        ivBack = findViewById(R.id.back_register);
        ivClear = findViewById(R.id.clear);
        ivSubmit = findViewById(R.id.submit);
        ivUploadPic = findViewById(R.id.upload_picture);
        ivUploadPicIcon = findViewById(R.id.upload_add);

        edAccount = findViewById(R.id.ed_account);
        edPassword = findViewById(R.id.ed_password);
        edNickName = findViewById(R.id.ed_nickname);
        edTel = findViewById(R.id.ed_tel);
        edEmail = findViewById(R.id.ed_email);

        tilAccount = findViewById(R.id.lo_account);
        tilPassword = findViewById(R.id.lo_password);
        tilNickName = findViewById(R.id.lo_nickname);
        tilTel = findViewById(R.id.lo_tel);
        tilEmail = findViewById(R.id.lo_email);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }
}