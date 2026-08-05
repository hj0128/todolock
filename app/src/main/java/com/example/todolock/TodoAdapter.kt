package com.example.todolock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.todolock.databinding.ItemTodoBinding

class TodoAdapter(
    private val items: MutableList<Todo>,
    private val onToggle: (Todo) -> Unit,
    private val onDelete: ((Todo) -> Unit)? = null
) : RecyclerView.Adapter<TodoAdapter.VH>() {

    inner class VH(val b: ItemTodoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTodoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        TodoRow.bind(holder.b, items[position], onToggle, onDelete)
    }

    fun submit(newItems: List<Todo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
