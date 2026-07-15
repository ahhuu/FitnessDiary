package com.cz.fitnessdiary.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cz.fitnessdiary.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Unified diet AI entry point. */
public class AiDietEntryBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "AiDietEntryBottomSheet";
    public static final String REQUEST_KEY = "ai_diet_entry_request";
    public static final String RESULT_TYPE = "result_type";
    public static final String RESULT_TEXT = "result_text";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_BARCODE = "barcode";

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_ai_diet_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextInputEditText input = view.findViewById(R.id.et_diet_description);
        MaterialButton textButton = view.findViewById(R.id.btn_diet_text);
        MaterialButton imageButton = view.findViewById(R.id.btn_diet_image);
        MaterialButton barcodeButton = view.findViewById(R.id.btn_diet_barcode);

        textButton.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(requireContext(), "请先描述这餐吃了什么", Toast.LENGTH_SHORT).show();
                return;
            }
            sendResult(TYPE_TEXT, text);
        });
        imageButton.setOnClickListener(v -> sendResult(TYPE_IMAGE, ""));
        barcodeButton.setOnClickListener(v -> sendResult(TYPE_BARCODE, ""));
    }

    private void sendResult(String type, String text) {
        Bundle result = new Bundle();
        result.putString(RESULT_TYPE, type);
        result.putString(RESULT_TEXT, text);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismissAllowingStateLoss();
    }
}
