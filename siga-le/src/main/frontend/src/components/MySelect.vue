<template>
  <div>
    <label for="orgao">{{label}}</label>
    <div v-if="!edit">{{nome}}</div>
    <select v-if="edit" :disabled="disabled" :id="name" class="form-control"  v-bind:value="value" v-on:input="$emit('input', $event.target.value)" v-on:change="$emit('change')" :name="name" :class="{ 'is-invalid': error }">
      <option disabled selected hidden :value="undefined">[Selecionar]</option>
      <option v-for="l in list" :value="l.id" :key="l.id">{{l.nome}}</option>
    </select>
    <div v-if="error" class="invalid-feedback">{{error}}</div>
  </div>
</template>

<script>
export default {
  name: 'my-select',
  props: ['value', 'label', 'name', 'list', 'edit', 'error', 'disabled'],
  data() {
    return {
    }
  },
  computed: {
    nome: function() {
      for (var i = 0; i < this.list.length; i++) {
        if (this.list[i].id === this.value) return this.list[i].nome
      }
      return ''
    }
  }
}
</script>
