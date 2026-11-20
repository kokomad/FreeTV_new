package com.khader.freetv

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.khader.freetv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var favs: Favorites
    private lateinit var prefs: Prefs
    private lateinit var adapter: ChannelAdapter
    private lateinit var look: Look
    private var current: List<Channel> = emptyList()
    private var section: Section = Sections.all[1]
    private var query = ""

    private val modes = listOf(
        "internal" to "▶ المشغل الداخلي (مدمج)",
        "mx" to "MX Player",
        "vlc" to "VLC",
        "other" to "مشغل آخر (أختاره من قائمة الجوال)",
        "ask" to "اسألني في كل مرة"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        favs = Favorites(this)
        prefs = Prefs(this)
        look = Looks.all[prefs.look]

        adapter = ChannelAdapter(
            look = look,
            isFav = { favs.has(it.url) },
            onClick = { play(it) },
            onLong = { ch -> External.choose(this, ch.name, ch.url) { playInternal(ch) } },
            onFav = {
                favs.toggle(it)
                if (section.url == null) { current = favs.all(); render() } else adapter.notifyDataSetChanged()
            }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        var first: Chip? = null
        Sections.all.forEach { s ->
            val chip = Chip(this).apply {
                text = s.title
                isCheckable = true
                setOnCheckedChangeListener { _, checked -> if (checked) select(s) }
            }
            b.chips.addView(chip)
            if (s === Sections.all[1]) first = chip
        }
        b.search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; render() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })
        b.settings.setOnClickListener { showMenu() }
        b.retry.setOnClickListener { select(section) }
        applyLook()
        first?.isChecked = true
    }

    // ---------- التصميم ----------
    private fun applyLook() {
        b.bg.setImageResource(look.bg)
        b.scrim.setBackgroundColor(look.scrim)
        b.title.setTextColor(look.title)
        b.title.setShadowLayer(20f, 0f, 0f, look.accent)
        b.settings.setTextColor(look.accent)
        b.search.background = Ui.card(this, look.card, look.accent.alpha(0x88), 14)
        b.status.setTextColor(look.sub)
        b.progress.indeterminateTintList = ColorStateList.valueOf(look.accent)
        b.retry.backgroundTintList = ColorStateList.valueOf(look.accent)
        b.retry.setTextColor(Color.WHITE)
        for (i in 0 until b.chips.childCount) styleChip(b.chips.getChildAt(i) as Chip)
        adapter.look = look
        adapter.notifyDataSetChanged()
    }

    private fun styleChip(chip: Chip) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(look.accent, look.card))
        chip.setTextColor(Color.WHITE)
        chip.chipStrokeColor = ColorStateList.valueOf(look.accent.alpha(0xAA))
        chip.chipStrokeWidth = Ui.dp(this, 1)
        chip.isCheckedIconVisible = false
    }

    // ---------- القوائم والإعدادات ----------
    private fun showMenu() {
        val items = arrayOf("🎨 تغيير التصميم", "▶ المشغل الافتراضي", "ℹ️ عن المطور")
        AlertDialog.Builder(this).setTitle("الإعدادات").setItems(items) { _, i ->
            when (i) {
                0 -> chooseLook()
                1 -> choosePlayer(false)
                else -> Dev.showAbout(this)
            }
        }.show()
    }

    private fun chooseLook() {
        val names = Looks.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("اختر التصميم")
            .setSingleChoiceItems(names, prefs.look) { d, i ->
                prefs.look = i; look = Looks.all[i]; applyLook(); d.dismiss()
            }.show()
    }

    private fun choosePlayer(first: Boolean, onDone: () -> Unit = {}) {
        val cur = modes.indexOfFirst { it.first == prefs.player }
        AlertDialog.Builder(this)
            .setTitle(if (first) "اختر المشغل لتشغيل القنوات" else "المشغل الافتراضي")
            .setSingleChoiceItems(modes.map { it.second }.toTypedArray(), cur) { d, i ->
                prefs.player = modes[i].first
                d.dismiss()
                Toast.makeText(this, "تم الحفظ — يمكنك تغييره من ⚙", Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setCancelable(!first)
            .show()
    }

    // ---------- التشغيل ----------
    private fun playInternal(ch: Channel) {
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("name", ch.name).putExtra("url", ch.url))
    }

    private fun play(ch: Channel) {
        when (prefs.player) {
            null -> choosePlayer(true) { play(ch) }
            "mx" -> if (!External.mx(this, ch.name, ch.url)) playInternal(ch)
            "vlc" -> if (!External.vlc(this, ch.name, ch.url)) playInternal(ch)
            "other" -> External.any(this, ch.name, ch.url)
            "ask" -> External.choose(this, ch.name, ch.url) { playInternal(ch) }
            else -> playInternal(ch)
        }
    }

    // ---------- تحميل القنوات ----------
    private fun select(s: Section) {
        section = s
        b.retry.visibility = View.GONE
        b.status.visibility = View.GONE
        val url = s.url
        if (url == null) { current = favs.all(); render(); return }
        b.progress.visibility = View.VISIBLE
        current = emptyList(); render()
        lifecycleScope.launch {
            try {
                val list = Repo.get(url)
                if (section !== s) return@launch
                current = list
                b.progress.visibility = View.GONE
                render()
            } catch (e: Exception) {
                if (section !== s) return@launch
                b.progress.visibility = View.GONE
                b.status.text = "تعذّر تحميل القنوات. تحقق من الإنترنت."
                b.status.visibility = View.VISIBLE
                b.retry.visibility = View.VISIBLE
            }
        }
    }

    private fun render() {
        val list = if (query.isEmpty()) current else current.filter { it.name.contains(query, true) }
        adapter.submit(list)
        if (b.progress.visibility != View.VISIBLE && b.retry.visibility != View.VISIBLE) {
            if (list.isEmpty()) {
                b.status.text = if (section.url == null) "لا توجد قنوات مفضلة بعد — اضغط ♡ بجانب أي قناة" else "لا توجد نتائج"
                b.status.visibility = View.VISIBLE
            } else b.status.visibility = View.GONE
        }
    }
}

class ChannelAdapter(
    var look: Look,
    private val isFav: (Channel) -> Boolean,
    private val onClick: (Channel) -> Unit,
    private val onLong: (Channel) -> Unit,
    private val onFav: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) { items = list; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
        val fav: TextView = v.findViewById(R.id.fav)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = items[i]
        val ctx = h.itemView.context
        h.itemView.background = Ui.card(ctx, look.card, look.accent.alpha(0x66), 16)
        h.logo.background = Ui.card(ctx, 0x33FFFFFF, Color.TRANSPARENT, 12)
        h.fav.setTextColor(look.accent)
        h.name.text = c.name
        h.group.text = c.group
        h.fav.text = if (isFav(c)) "♥" else "♡"
        h.logo.load(c.logo.ifEmpty { null }) {
            placeholder(ColorDrawable(Color.TRANSPARENT))
            error(ColorDrawable(Color.TRANSPARENT))
        }
        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.setOnLongClickListener { onLong(c); true }
        h.fav.setOnClickListener { onFav(c) }
    }
}
