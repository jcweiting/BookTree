package com.book.second_book_exchange.fragment;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.book.second_book_exchange.AddBookActivity;
import com.book.second_book_exchange.AddBookBasicData;
import com.book.second_book_exchange.BookInfoActivity;
import com.book.second_book_exchange.CustomCartSelect;
import com.book.second_book_exchange.api.ApiTool;
import com.book.second_book_exchange.api.UserData;
import com.book.second_book_exchange.log.JoyceLog;
import com.book.second_book_exchange.recyclerview.HomeAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.book.second_book_exchange.R;

import java.util.ArrayList;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class HomeFragment extends Fragment {

    public static final String ADD_BOOK_BASIC_DATA = "Add book basic data";
    public static final String CART = "Cart";
    public static final String LOVE = "Love";
    private ImageView ivPlus;
    private RecyclerView recyclerView;
    private OnChangeTabListener onChangeTabListener;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private ConstraintLayout root;
    private View mask;
    private int defaultQty = 1;
    private int qtyMax = 0 ;
    private CompositeDisposable compositeDisposable;
    private HomeAdapter homeAdapter;
    private ProgressBar progressBar;
    private ArrayList<AddBookBasicData> bookDataArr;
    private ArrayList<AddBookBasicData> newPostArray;
    private UserData userData;

    public void setOnChangeTabListener(OnChangeTabListener onChangeTabListener) {
        this.onChangeTabListener = onChangeTabListener;
    }

    public static HomeFragment newInstance() {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        compositeDisposable = new CompositeDisposable();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //fragment??????layout (??????view)
        View view =inflater.inflate(R.layout.fragment_home,container,false);
        initView(view);

        //??????RecyclerView??????============================================
        //1???2???(?????????)
        GridLayoutManager grid = new GridLayoutManager(getContext(), 2);
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {

                if (position == 0){
                    return 2;
                }
                return 1;
            }
        });

        recyclerView.setLayoutManager(grid);
        //================================================================

        return view;
    }

    //???????????????onResume??????,HomePage??????????????????????????????
    @Override
    public void onResume() {
        super.onResume();

        searchBookInfo();
    }

    private void searchBookInfo() {

        if (mAuth.getCurrentUser() == null) {
            onChangeTabListener.onChangeTabIcon();
            showHint("??????????????????");

            JoyceLog.i("HomeFragment | ??????????????????");
            return;
        }

        userData = new UserData(user.getUid());
        JoyceLog.i("my UId : "+userData.getUid());

        ApiTool.getRequestApi()
                .getAllList(userData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AddBookBasicData>>() {

                    //?????????,???????????????,????????????????????????
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                        //?????????onCreate new????????????
                        compositeDisposable.add(d);
                    }

                    //?????????????????????,??????????????????????????????,??????????????????************
                    @Override
                    public void onNext(@NonNull ArrayList<AddBookBasicData> bookListArray) {
                        JoyceLog.i(new Gson().toJson(bookListArray));

                        bookDataArr = bookListArray;

                        showBookList(bookListArray);
                        progressBar.setVisibility(View.GONE);
                    }

                    //????????????????????????
                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("HomeFragment | allBookList | Error: "+e);
                    }

                    //?????????????????????
                    @Override
                    public void onComplete() {
                        JoyceLog.i("HomeFragment | allBookList | onComplete");
                    }
                });
    }

    //?????????,???????????????,????????????????????????
    @Override
    public void onDestroy() {
        super.onDestroy();

        compositeDisposable.clear();
    }

    private void showBookList(ArrayList<AddBookBasicData> bookArray) {

        //?????????????????????(3??????)===============================================================

        long timeStamp = System.currentTimeMillis();    //??????????????????
        long perDay = 24*60*60*1000;    // 1??? = 1000??????

        newPostArray = new ArrayList<>();

        for(int i = 0 ; i < bookArray.size() ; i++){

            long uploadDate = bookArray.get(i).getTime();

            //???????????????3??????????????????
            if ((timeStamp - uploadDate) / perDay < 3 ){
                newPostArray.add(bookArray.get(i));
            }
        }
        //================================================================================

        homeAdapter = new HomeAdapter();
        homeAdapter.setInfo(bookArray,newPostArray);
        recyclerView.setAdapter(homeAdapter);

        //RecyclerView???????????????????????????
        homeAdapter.setListener(new HomeAdapter.OnBookInfoClickListener() {
            @Override
            public void onClick(AddBookBasicData addBookBasicData) {

                Intent it = new Intent(getContext(),BookInfoActivity.class);
                it.putExtra(ADD_BOOK_BASIC_DATA,addBookBasicData);
                startActivity(it);
            }
        });

        homeAdapter.setListenerBtn(new HomeAdapter.OnButtonClickListener() {
            @Override
            public void onClickCart(AddBookBasicData addBookBasicData) {

                if (mAuth.getCurrentUser() == null){
                    showHint("??????????????????");
                    return;
                }

                addToCart(bookArray, addBookBasicData);
            }

            @Override
            public void onClickLove(AddBookBasicData addBookBasicDataObject) {

                if (mAuth.getCurrentUser() == null){
                    showHint("??????????????????");
                    return;
                }

                for (int i = 0 ; i < bookArray.size() ; i++){
                    AddBookBasicData basicDataLove = bookArray.get(i);

                    if (basicDataLove.getBookName().equals(addBookBasicDataObject.getBookName())){

                        //?????????myUid
                        basicDataLove.setMyUid(user.getUid());
                        if (!addBookBasicDataObject.isSelectHeart()){
                            addToFavorite(basicDataLove);
                        }else {
                            deleteFavorite(basicDataLove);
                        }

//                        JoyceLog.i("basicDataLove: "+new Gson().toJson(basicDataLove));
                    }
                }
            }

            @Override
            public void onClickChangePage(AddBookBasicData addBookBasicData) {
                Intent intent = new Intent(getContext(),BookInfoActivity.class);
                intent.putExtra(ADD_BOOK_BASIC_DATA,addBookBasicData);
                startActivity(intent);
            }
        });
    }

    private void deleteFavorite(AddBookBasicData basicDataLove) {
        basicDataLove.setMyUid(user.getUid());
        ApiTool.getRequestApi()
                .deleteFavorite(basicDataLove)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<AddBookBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull AddBookBasicData addBookBasicData) {
                        showHint("???????????????????????????");

                        for (AddBookBasicData bookData : bookDataArr){

                            if (basicDataLove.getBookName().equals(bookData.getBookName())){
                                bookData.setSelectHeart(false);
                            }
                        }

                        homeAdapter.setInfo(bookDataArr,newPostArray);
                        homeAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("HomeFragment | addToFavorite | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("HomeFragment | addToFavorite | onComplete");
                    }
                });

    }

    private void addToCart(ArrayList<AddBookBasicData> bookArray, AddBookBasicData addBookBasicData) {

        defaultQty = 1;

        CustomCartSelect customCartSelect = new CustomCartSelect();
        customCartSelect.showView(mask,root,getActivity());
        customCartSelect.setTvName(addBookBasicData.getBookName());
        customCartSelect.setTvUnitPrice(addBookBasicData.getUnitPrice());
        customCartSelect.setIvCover(addBookBasicData.getPhotoUrl());

        customCartSelect.setListener(new CustomCartSelect.OnButtonClickListener() {
            @Override
            public void onClickToCart(String tvQty) {

                for (int i = 0 ; i < bookArray.size() ; i++){
                    AddBookBasicData bookDataObject = bookArray.get(i);

                    if (bookDataObject.getBookName().equals(addBookBasicData.getBookName())){
                        qtyMax = Integer.parseInt(bookArray.get(i).getQty());
                        bookDataObject.setQty(tvQty);   //????????????????????????
                        addToCart(bookDataObject);
                        break;
                    }
                }
            }

            @Override
            public void onClickMinus() {

                defaultQty = defaultQty - 1 ;

                if (defaultQty <= 0){
                    showHint("??????????????????0");

                    defaultQty = 1;
                    customCartSelect.setQty(defaultQty);
                    return;
                }

                customCartSelect.setQty(defaultQty);
            }

            @Override
            public void onClickAdd() {

                defaultQty = defaultQty + 1 ;

                for (int i = 0 ; i < bookArray.size() ; i++){
                    AddBookBasicData bookBasicData = bookArray.get(i);

                    if (bookBasicData.getBookName().equals(addBookBasicData.getBookName())){
                        qtyMax = Integer.parseInt(bookArray.get(i).getQty());
                    }
                }

                if (defaultQty > qtyMax){
                    showHint("?????????????????????");
                    defaultQty = qtyMax;
                    return;
                }

                customCartSelect.setQty(defaultQty);
            }
        });
    }

    private void addToFavorite(AddBookBasicData basicDataLove) {

        ApiTool.getRequestApi()
                .addFavorite(basicDataLove)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<AddBookBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull AddBookBasicData addBookBasicData) {
                        showHint("????????????????????????");

                        for (AddBookBasicData bookData : bookDataArr){

                            if (basicDataLove.getBookName().equals(bookData.getBookName())){
                                bookData.setSelectHeart(true);
                            }
                        }

                        homeAdapter.setInfo(bookDataArr,newPostArray);
                        homeAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("HomeFragment | addToFavorite | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("HomeFragment | addToFavorite | onComplete");
                    }
                });
    }

    private void addToCart(AddBookBasicData bookDataObject) {

        JoyceLog.i("click cart uid : "+new Gson().toJson(bookDataObject));

        bookDataObject.setMyUid(user.getUid());

        ApiTool.getRequestApi()
                .addToCart(bookDataObject)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<AddBookBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull AddBookBasicData addBookBasicData) {
                        showHint("??????????????????????????????");
                        defaultQty = 1 ;

                        JoyceLog.i("HomeFragment | addToCart() | ??????????????????????????????");
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("HomeFragment | addToCart() | Error: "+e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("HomeFragment | addToCart() | onComplete");
                    }
                });
    }

    private void initView(View view) {

        recyclerView = view.findViewById(R.id.recyclerview_home);
        ivPlus = view.findViewById(R.id.plus);
        root = view.findViewById(R.id.root);
        mask = view.findViewById(R.id.mask);
        progressBar = view.findViewById(R.id.progress_bar);

        /*
        //recyclerViewHome?????? & 1??????2???
        recyclerViewHome.addItemDecoration(new EqualSpacingItemDecoration(25)); // 16px. In practice, you'll want to use getDimensionPixelSize
        recyclerViewHome.setLayoutManager(new GridLayoutManager(getContext(),2));

        //recyclerViewBook??????
        LinearLayoutManager manager = new LinearLayoutManager(getContext());
        manager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerViewBook.setLayoutManager(manager);

        //??????action bar title??????
        tvActionBarTitle = view.findViewById(R.id.action_bar_title);
        tvActionBarTitle.setTypeface(Typeface.createFromAsset(getActivity().getAssets(), "struckbase.otf"));
         */

        ivPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //?????????????????????,icon???????????????icon
                if (mAuth.getCurrentUser() == null){
                    onChangeTabListener.onChangeTabIcon();
                    showHint("??????????????????");
                    return;
                }

                Intent intent = new Intent(getContext(), AddBookActivity.class);
                startActivity(intent);
            }
        });
    }

    private void showHint(String content) {
        Toast.makeText(getActivity(), content, Toast.LENGTH_SHORT).show();
    }

    //????????????icon?????????
    public interface OnChangeTabListener{
        void onChangeTabIcon();
    }
}