package com.book.second_book_exchange.fragment;

import static com.book.second_book_exchange.fragment.HomeFragment.ADD_BOOK_BASIC_DATA;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.book.second_book_exchange.AddBookBasicData;
import com.book.second_book_exchange.BookInfoActivity;
import com.book.second_book_exchange.BookInsideData;
import com.book.second_book_exchange.BookOuterData;
import com.book.second_book_exchange.CheckOut;
import com.book.second_book_exchange.R;
import com.book.second_book_exchange.ShipmentSelect;
import com.book.second_book_exchange.SpinnerList;
import com.book.second_book_exchange.api.ApiTool;
import com.book.second_book_exchange.api.UserData;
import com.book.second_book_exchange.log.JoyceLog;
import com.book.second_book_exchange.recyclerview.CartOuterAdapter;
import com.book.second_book_exchange.tool.ViewDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Iterator;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class CartFragment extends Fragment {

    public static final String OUTER_DATA_ARR = "OuterDataArr";
    private RecyclerView recyclerView;
    private FirebaseFirestore db;
    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private OnChangeCartListener onChangeCartListener;
    private int qtyMax = 0;
    private CompositeDisposable compositeDisposable;
    private ArrayList<AddBookBasicData> cartArray, allBookArray;
    private AddBookBasicData bookList;
    private CheckBox checkBox;
    private TextView tvCheckOut, tvSum, tvNoData;
    private CartOuterAdapter outerAdapter;
    private ArrayList<BookOuterData> outerDataList;
    private ConstraintLayout root;
    private View mask;
    private int bottomPrice = 0;
    private ConstraintLayout consCheckOut;
    private boolean isCheckOutStatus = false;
    private ProgressBar progressBar;

    public void setOnChangeCartListener(OnChangeCartListener onChangeCartListener) {
        this.onChangeCartListener = onChangeCartListener;
    }

    public static CartFragment newInstance() {
        CartFragment fragment = new CartFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        compositeDisposable = new CompositeDisposable();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (user == null) {
            onChangeCartListener.onChangeCartIcon();
            showHint("??????????????????");
            return;
        }

        //????????????????????????Replace,Fragment???????????????,?????????????????????,????????????????????????onCreateView,?????????????????????onResume??????????????????,??????onCreateView?????????
        //??????????????????????????????Activity????????????????????????onResume
        checkBookMaxQty();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_cart, container, false);
        initView(view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));


        tvCheckOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //????????????ArrayList??????
                Iterator<BookOuterData> iterator = outerDataList.iterator();
                while (iterator.hasNext()) {

                    BookOuterData outerData = iterator.next();

                    for (BookInsideData insideData : outerData.getProductLists()) {

                        //????????????????????????, ???????????????outerDataList?????????
                        if (!insideData.isSelectedProduct()) {
                            outerData.getProductLists().remove(insideData);
                        }
                    }
                    //??????outerData?????????, ??????arr????????????
                    if (outerData.getProductLists().isEmpty()) {
                        iterator.remove();
                    }
                }

                JoyceLog.i("CartFragment | outerDataList: " + new Gson().toJson(outerDataList));

                Intent intent = new Intent(getContext(), CheckOut.class);
                intent.putExtra(OUTER_DATA_ARR, outerDataList);
                startActivity(intent);
            }
        });

        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //???????????????,checkBox?????????
                boolean isAllSelect = checkBox.isChecked();

                //?????????????????????????????????????????????????????????,?????????????????????
                for (BookOuterData bookDataObject : outerDataList) {

                    bookDataObject.setAllSelected(isAllSelect);

                    //?????????????????? or ??????????????????
                    //?????????????????????,???BookInsideData
                    for (BookInsideData bookInsideData : bookDataObject.getProductLists()) {
                        bookInsideData.setSelectedProduct(bookDataObject.isAllSelected());
//                        JoyceLog.i("CartFragment | bookDataObject.isAllSelected(): " + bookDataObject.isAllSelected());
                    }
                }

                outerAdapter.setBookDataArr(outerDataList);     //??????????????????????????????Adapter
                outerAdapter.notifyDataSetChanged();    //??????????????????
                updateCheckOutPrice(outerDataList);
                checkBtnEnable();
            }
        });

        return view;
    }

    private void showHint(String content) {
        Toast.makeText(getContext(), content, Toast.LENGTH_SHORT).show();
    }

    private void showData(ArrayList<AddBookBasicData> allBookArray) {

        JoyceLog.i("CartFragment | uid : " + user.getUid());

        ApiTool.getRequestApi()
                .allCartList(new UserData(user.getUid()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AddBookBasicData>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull ArrayList<AddBookBasicData> cartListArray) {

                        progressBar.setVisibility(View.GONE);

                        JoyceLog.i("CartFragment | ?????????????????????: " + new Gson().toJson(cartListArray));

                        if (cartListArray.isEmpty()) {
                            tvNoData.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                            consCheckOut.setVisibility(View.GONE);
                            return;
                        }

                        cartArray = cartListArray;

                        AddBookBasicData bookListObject = new AddBookBasicData();
                        AddBookBasicData cartListObject = new AddBookBasicData();

                        for (int i = 0; i < cartListArray.size(); i++) {

                            for (int j = 0; j < allBookArray.size(); j++) {

                                cartListObject = cartListArray.get(i);
                                bookListObject = allBookArray.get(j);

                                if (cartListObject.getBookName().equals(bookListObject.getBookName())) {

                                    String cartQty = cartListObject.getQty();
                                    String bookMax = bookListObject.getQty();

                                    bookList = bookListObject;

                                    if (cartQty == null || cartQty.equals("") || bookMax == null || bookMax.equals("")) {
                                        continue;
                                    }

                                    if (Integer.parseInt(cartQty.trim()) > Integer.parseInt(bookMax.trim())) {

                                        cartListObject.setQty(bookMax);
                                        showAlertDialog("????????????????????????,???????????????");
                                        editCartQty(cartListObject);
                                        JoyceLog.i("CartFragment | bookQtyMax: " + bookMax);
                                    }
                                }
                            }
                        }

                        outerDataList = new ArrayList<>();

                        //????????????????????? ???????????? "?????????????????????" ?????? "NTD 0"
                        for (AddBookBasicData data : cartListArray) {

                            //????????????????????? ????????????????????????????????????
                            if (outerDataList.isEmpty()) {

                                //???AddBookBasicData??????????????????BookOuterData
                                BookOuterData bookOuterData = new BookOuterData();
                                bookOuterData.setUserEmail(data.getUserEmail());
                                bookOuterData.setUploaderUid(data.getUploaderUid());
                                bookOuterData.setMyUid(data.getMyUid());
                                bookOuterData.setShipmentWay("?????????????????????");
                                bookOuterData.setShipmentFee(0);

                                //???AddBookBasicData??????????????????BookInsideData
                                ArrayList<BookInsideData> insideDataList = new ArrayList<>();
                                BookInsideData bookInsideData = new BookInsideData();
                                bookInsideData.setBookName(data.getBookName());
                                bookInsideData.setQty(data.getQty());
                                bookInsideData.setMyUid(user.getUid());
                                bookInsideData.setId(data.getId());
                                bookInsideData.setUnitPrice(data.getUnitPrice());
                                bookInsideData.setPhotoUrl(data.getPhotoUrl());
                                bookInsideData.setUploaderUid(data.getUploaderUid());
                                insideDataList.add(bookInsideData);

                                //???ArrayList<BookInsideData> insideDataList  ??????bookOuterData
                                bookOuterData.setProductLists(insideDataList);

                                //???bookOuterData  ??????ArrayList<BookOuterData> outerDataList
                                outerDataList.add(bookOuterData);
                                continue;
                            }

                            boolean isFoundSameUid = false;
                            for (BookOuterData outerData : outerDataList) {

                                //?????????Uid????????????????????????
                                if (outerData.getUploaderUid().equals(data.getUploaderUid())) {

                                    BookInsideData bookInsideData = new BookInsideData();

                                    bookInsideData.setBookName(data.getBookName());
                                    bookInsideData.setQty(data.getQty());
                                    bookInsideData.setId(data.getId());
                                    bookInsideData.setMyUid(user.getUid());
                                    bookInsideData.setUnitPrice(data.getUnitPrice());
                                    bookInsideData.setPhotoUrl(data.getPhotoUrl());
                                    bookInsideData.setUploaderUid(data.getUploaderUid());
                                    outerData.getProductLists().add(bookInsideData);
                                    isFoundSameUid = true;
                                }
                            }

                            //?????????????????????????????????UID ??????????????????BookOuterData
                            if (!isFoundSameUid) {

                                //???AddBookBasicData??????????????????BookOuterData
                                BookOuterData bookOuterData = new BookOuterData();
                                bookOuterData.setUserEmail(data.getUserEmail());
                                bookOuterData.setUploaderUid(data.getUploaderUid());
                                bookOuterData.setMyUid(data.getMyUid());
                                bookOuterData.setShipmentWay("?????????????????????");
                                bookOuterData.setShipmentFee(0);

                                //???AddBookBasicData??????????????????BookInsideData
                                ArrayList<BookInsideData> insideDataList = new ArrayList<>();
                                BookInsideData bookInsideData = new BookInsideData();
                                bookInsideData.setBookName(data.getBookName());
                                bookInsideData.setQty(data.getQty());
                                bookInsideData.setMyUid(user.getUid());
                                bookInsideData.setUploaderUid(data.getUploaderUid());
                                bookInsideData.setUnitPrice(data.getUnitPrice());
                                bookInsideData.setPhotoUrl(data.getPhotoUrl());
                                bookInsideData.setId(data.getId());
                                insideDataList.add(bookInsideData);

                                //???ArrayList<BookInsideData> insideDataList  ??????bookOuterData
                                bookOuterData.setProductLists(insideDataList);

                                //???bookOuterData  ??????ArrayList<BookOuterData> outerDataList
                                outerDataList.add(bookOuterData);
                            }
                        }

                        JoyceLog.i("cartList : " + new Gson().toJson(outerDataList));

                        //???????????????????????????????????????????????????
                        outerAdapter = new CartOuterAdapter();
                        outerAdapter.setBookDataArr(outerDataList);
                        recyclerView.setAdapter(outerAdapter);

                        //======================================================================

                        outerAdapter.setListener(new CartOuterAdapter.OnInsideAdapterListener() {

                            @Override
                            public void onClickAllCheckBoxListener(BookOuterData bookOuterData) {

                                //?????????????????????????????????????????????????????????,?????????????????????
                                for (BookOuterData bookDataObject : outerDataList) {

                                    if (bookOuterData.getUploaderUid().equals(bookDataObject.getUploaderUid())) {

                                        //????????????????????????,???????????????????????????????????????????????? ?????? : checkBox ??????????????? ??????????????????????????????CHECKBOX??????????????????????????? , ??????????????????,?????????????????????????????????
                                        //?????????????????? or ??????????????????
                                        //?????????????????????,???BookInsideData
                                        for (BookInsideData bookInsideData : bookDataObject.getProductLists()) {
                                            bookInsideData.setSelectedProduct(bookDataObject.isAllSelected());
                                        }

                                        break;
                                    }
                                }

                                outerAdapter.setBookDataArr(outerDataList);     //??????????????????????????????Adapter
                                outerAdapter.notifyDataSetChanged();    //??????????????????
                                updateCheckOutPrice(outerDataList);
                                checkBtnEnable();
                            }

                            @Override
                            public void onSingleItemSelect(BookInsideData bookInsideData, BookOuterData bookOuterData) {

                                for (BookOuterData outerData : outerDataList) {

                                    if (outerData.getUploaderUid().equals(bookOuterData.getUploaderUid())) {

                                        //????????????CheckBox??????
                                        outerData.setAllSelected(false);

                                        for (BookInsideData insideData : outerData.getProductLists()) {

                                            //?????????????????????uid, ???????????????????????????
                                            if (bookInsideData.getBookName().equals(insideData.getBookName())) {
                                                insideData.setSelectedProduct(bookInsideData.isSelectedProduct());

                                                if (insideData.isSelectedProduct()) {
                                                    JoyceLog.i("CartFragment | insideData.isSelectedProduct(): " + insideData.isSelectedProduct());
                                                }
                                            }
                                        }

                                        break;
                                    }
                                }

                                outerAdapter.setBookDataArr(outerDataList);
                                outerAdapter.notifyDataSetChanged();
                                checkBtnEnable();

                                updateCheckOutPrice(outerDataList);
                            }

                            @Override
                            public void onClickShipmentWay(BookOuterData bookOuterData) {

                                ShipmentSelect shipmentSelect = new ShipmentSelect();
                                shipmentSelect.showView(mask, root, getActivity());

                                shipmentSelect.setOnClickShipment(new ShipmentSelect.OnClickShipment() {
                                    @Override
                                    public void onClickShipment(SpinnerList data) {

                                        for (BookOuterData outerData : outerDataList) {

                                            if (outerData.getUploaderUid().equals(bookOuterData.getUploaderUid())) {
                                                outerData.setShipmentWay(data.getShipmentWay());
                                                outerData.setShipmentFee(data.getPrice());

                                            }
                                        }
                                        outerAdapter.setBookDataArr(outerDataList);
                                        JoyceLog.i("CartFragment | onClickShipmentWay: " + new Gson().toJson(outerDataList));

                                        outerAdapter.notifyDataSetChanged();

                                        updateCheckOutPrice(outerDataList);
                                        checkBtnEnable();
                                    }
                                });
                            }

                            //???????????????+1
                            @Override
                            public void onClickAdd(int unitPrice, BookInsideData data) {

                                JoyceLog.i("??????");

                                //???????????????                            //?????????ArrayList  --> for each ??????
                                for (AddBookBasicData bookBasicData : allBookArray) {

                                    if (bookBasicData.getBookName().equals(data.getBookName())) {
                                        qtyMax = Integer.parseInt(bookBasicData.getQty());
                                    }
                                }

                                int qty = Integer.parseInt(data.getQty().trim()) + 1;

                                if (qty > qtyMax) {
                                    showHint("?????????????????????");
                                    return;
                                }

                                //???qty?????????????????????(outerDataList)
                                for (BookOuterData outerData : outerDataList) {

                                    for (BookInsideData insideData : outerData.getProductLists()) {
                                        if (insideData.getBookName().equals(data.getBookName())) {
                                            insideData.setQty(qty + "");
                                        }
                                    }
                                }

                                //???Adapter???????????????
                                outerAdapter.setBookDataArr(outerDataList);
                                outerAdapter.notifyDataSetChanged();

                                updateCheckOutPrice(outerDataList);
                            }

                            //???????????????-1
                            @Override
                            public void onClickMinus(int unitPrice, BookInsideData data) {

                                int qty = Integer.parseInt(data.getQty()) - 1;

                                if (qty <= 0) {
                                    showHint("??????????????????0");
                                    return;
                                }

                                for (BookOuterData outerData : outerDataList) {

                                    for (BookInsideData insideData : outerData.getProductLists()) {
                                        if (insideData.getBookName().equals(data.getBookName())) {
                                            insideData.setQty(qty + "");
                                        }
                                    }
                                }

                                outerAdapter.setBookDataArr(outerDataList);
                                outerAdapter.notifyDataSetChanged();

                                updateCheckOutPrice(outerDataList);
                            }

                            //??????????????????????????????BookInfo
                            @Override
                            public void onClickCover(BookInsideData bookInsideData) {

                                AddBookBasicData bookBasicData = null;

                                //?????????AddBookBasicData
                                for (AddBookBasicData data : cartArray) {
                                    if (data.getId() == bookInsideData.getId()) {
                                        bookBasicData = data;
                                    }
                                }

                                Intent intent = new Intent(getContext(), BookInfoActivity.class);
                                intent.putExtra(ADD_BOOK_BASIC_DATA, bookBasicData);
                                startActivity(intent);
                            }

                            @Override
                            public void onClickDelete(BookInsideData bookInsideData) {

                                JoyceLog.i("CartFragment | onClickDelete | addBookBasicData: " + new Gson().toJson(bookInsideData));

                                //???????????????id????????????,???????????????bookInsideData???Id?????????addBookBasicData?????????
                                AddBookBasicData addBookBasicData = new AddBookBasicData();
                                addBookBasicData.setId(bookInsideData.getId());

                                ApiTool.getRequestApi()
                                        .deleteFromCart(addBookBasicData)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(new Observer<AddBookBasicData>() {
                                            @Override
                                            public void onSubscribe(@NonNull Disposable d) {
                                                compositeDisposable.add(d);
                                            }

                                            @Override
                                            public void onNext(@NonNull AddBookBasicData bookDataObject) {
                                                JoyceLog.i("CartFragment | onClickDelete | ?????????????????????: " + new Gson().toJson(bookDataObject));

                                                //??????????????????===========================================
                                                //???????????????????????? AddBookBasicData ?????????ID??????????????????????????????????????????ID ???????????????
                                                //?????????????????????
                                                for (BookOuterData bookOuterData : outerDataList) {
                                                    for (BookInsideData insideData : bookOuterData.getProductLists()) {
                                                        if (insideData.getId() == bookDataObject.getId()) {
                                                            bookOuterData.getProductLists().remove(insideData);

                                                            JoyceLog.i("CartFragment | ????????????");
                                                            break;
                                                        }
                                                    }
                                                }

                                                //????????????ArrayList???????????????
                                                //?????????????????????
                                                Iterator<BookOuterData> iterator = outerDataList.iterator();
                                                while (iterator.hasNext()) {

                                                    BookOuterData outerData = iterator.next();

                                                    if (outerData.getProductLists().isEmpty()) {
                                                        iterator.remove();
                                                    }
                                                }

                                                showHint("?????????????????????");

                                                //??????????????? ????????????????????? Adapter ??? ????????????
                                                outerAdapter.setBookDataArr(outerDataList);
                                                JoyceLog.i("CartFragment | deleteFromCart | outerDataList: " + new Gson().toJson(outerDataList));
                                                outerAdapter.notifyDataSetChanged();

                                                updateCheckOutPrice(outerDataList);

                                                //=====================================================
                                            }

                                            @Override
                                            public void onError(@NonNull Throwable e) {
                                                JoyceLog.i("CartFragment | onClickDelete | Error: " + e);
                                            }

                                            @Override
                                            public void onComplete() {
                                                JoyceLog.i("CartFragment | onClickDelete | onComplete");
                                            }
                                        });
                            }
                        });

                        //======================================================================

                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("CartFragment | allCartList | Error: " + e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("CartFragment | allCartList | onComplete");
                    }
                });
    }

    private void checkBtnEnable() {

        boolean isAbleToCheckOut = false;

        for (BookOuterData outerData : outerDataList) {

            if (outerData.getShipmentWay().contains("????????????")) {
                isAbleToCheckOut = false;
                continue;
            }

            for (BookInsideData insideData : outerData.getProductLists()) {

                if (insideData.isSelectedProduct() && !outerData.getShipmentWay().contains("????????????")) {
                    isAbleToCheckOut = true;
                }
            }

        }

        tvCheckOut.setEnabled(isAbleToCheckOut);
        tvCheckOut.setBackgroundResource(isAbleToCheckOut ? R.color.light_blueFF7CA3A8 : R.color.light_grey);
    }

    //????????????
    private void updateCheckOutPrice(ArrayList<BookOuterData> outerDataList) {

        bottomPrice = 0;

        for (BookOuterData outerData : outerDataList) {

            //???????????????????????????????????????0,?????????????????????
            //???????????????????????????
            int productTotalPrice = 0;

            for (BookInsideData insideData : outerData.getProductLists()) {

                //????????????
                if (insideData.isSelectedProduct()) {

                    String strUnitPrice = insideData.getUnitPrice();
                    String strQty = insideData.getQty();

                    if (strUnitPrice.equals("") || strUnitPrice.isEmpty() || strQty.equals("") || strQty.isEmpty()) {
                        showHint("????????????????????????");
                        return;
                    }

                    bottomPrice += Integer.parseInt(strUnitPrice.trim()) * Integer.parseInt(strQty.trim());
                    productTotalPrice += Integer.parseInt(strUnitPrice.trim()) * Integer.parseInt(strQty.trim());
                }
            }

            outerData.setSum((productTotalPrice + outerData.getShipmentFee()) + "");

            //????????????
            bottomPrice += outerData.getShipmentFee();
            outerData.setSum(bottomPrice + "");
            JoyceLog.i("CartFragment | bottomPrice: " + bottomPrice);

        }

        JoyceLog.i("CartFragment | bottomPrice: " + bottomPrice);
        tvSum.setText("NTD " + bottomPrice);

    }

    private void editCartQty(AddBookBasicData cartListObject) {

        cartListObject.setMyUid(user.getUid());

        ApiTool.getRequestApi()
                .editToCart(cartListObject)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<AddBookBasicData>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull AddBookBasicData addBookBasicData) {
                        JoyceLog.i("CartFragment | addToCart | qty: " + addBookBasicData.getQty());
                        showHint("????????????????????????");
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("CartFragment | addToCart | Error: " + e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("CartFragment | addToCart | onComplete");
                    }
                });
    }

    private void showAlertDialog(String content) {

        ViewDialog alert = new ViewDialog();
        alert.showDialog(getActivity(), content);
        alert.setOnAlertDialogClickListener(new ViewDialog.OnAlertDialogClickListener() {
            @Override
            public void onConfirm() {

            }

            @Override
            public void onCancel() {

            }
        });
    }

    //?????????????????????????????????, ???????????????????????????
    private void checkBookMaxQty() {

        UserData userData = new UserData(user.getUid());

        ApiTool.getRequestApi()
                .getAllList(userData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AddBookBasicData>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull ArrayList<AddBookBasicData> bookListArray) {
                        JoyceLog.i(new Gson().toJson(bookListArray));
                        allBookArray = bookListArray;

                        showData(bookListArray);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        JoyceLog.i("CartFragment | getAllList | Error: " + e);
                    }

                    @Override
                    public void onComplete() {
                        JoyceLog.i("CartFragment | getAllList | onComplete");
                    }
                });
    }

    private void initView(View view) {
        recyclerView = view.findViewById(R.id.recyclerview_cart);
        checkBox = view.findViewById(R.id.checkbox);
        tvCheckOut = view.findViewById(R.id.check_out);
        tvSum = view.findViewById(R.id.sum);
        mask = view.findViewById(R.id.mask);
        root = view.findViewById(R.id.root);
        tvNoData = view.findViewById(R.id.no_data);
        consCheckOut = view.findViewById(R.id.cons_checkout);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    public interface OnChangeCartListener {
        void onChangeCartIcon();
    }
}