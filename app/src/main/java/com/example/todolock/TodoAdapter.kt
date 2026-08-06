package com.example.todolock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.todolock.databinding.ItemHeaderBinding
import com.example.todolock.databinding.ItemTodoBinding

/** 목록 한 줄. 날짜별 화면 대신 '할 일 / 완료' 섹션으로 묶습니다. */
sealed class Row {
    data class Header(val title: String) : Row()
    data class Item(val todo: Todo) : Row()
}

class TodoAdapter(
    private val rows: MutableList<Row>,
    private val onToggle: (Todo) -> Unit,
    private val onDelete: ((Todo) -> Unit)? = null,
    private val onStar: ((Todo) -> Unit)? = null,
    private val onEdit: ((Todo) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private class HeaderVH(val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root)
    private class ItemVH(val b: ItemTodoBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemVH(ItemTodoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).b.tvHeader.text = row.title
            is Row.Item -> TodoRow.bind(
                (holder as ItemVH).b, row.todo, onToggle, onDelete, onStar, onEdit
            )
        }
    }

    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}