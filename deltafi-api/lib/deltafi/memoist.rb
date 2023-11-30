#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# frozen_string_literal: true

module Deltafi
  module Memoist
    def self.extended(extender)
      Memoist.memoist_eval(extender) do
        unless singleton_class.method_defined?(:memoized_methods)
          def self.memoized_methods
            @_memoized_methods ||= []
          end
        end
      end
    end

    def self.memoized_ivar_for(method_name, identifier = nil)
      "@#{memoized_prefix(identifier)}_#{escape_punctuation(method_name)}"
    end

    def self.unmemoized_method_for(method_name, identifier = nil)
      "#{unmemoized_prefix(identifier)}_#{method_name}".to_sym
    end

    def self.memoized_prefix(identifier = nil)
      if identifier
        "_memoized_#{identifier}"
      else
        '_memoized'.freeze
      end
    end

    def self.unmemoized_prefix(identifier = nil)
      if identifier
        "_unmemoized_#{identifier}"
      else
        '_unmemoized'.freeze
      end
    end

    def self.escape_punctuation(string)
      string = string.is_a?(String) ? string.dup : string.to_s.dup

      return string unless string.end_with?('?'.freeze, '!'.freeze)

      # A String can't end in both ? and !
      if string.sub!(/\?\Z/, '_query'.freeze)
      else
        string.sub!(/!\Z/, '_bang'.freeze)
      end
      string
    end

    def self.memoist_eval(klass, *args, &block)
      if klass.respond_to?(:class_eval)
        klass.class_eval(*args, &block)
      else
        klass.singleton_class.class_eval(*args, &block)
      end
    end

    def self.extract_reload!(method, args)
      if args.length == method.arity.abs + 1 && (args.last == true || args.last == :reload)
        reload = args.pop
      end
      reload
    end

    module InstanceMethods
      def memoize_all
        prime_cache
      end

      def unmemoize_all
        flush_cache
      end

      def memoized_structs(names)
        ref_obj = self.class.respond_to?(:class_eval) ? singleton_class : self
        structs = ref_obj.all_memoized_structs
        return structs if names.empty?

        structs.select { |s| names.include?(s.memoized_method) }
      end

      def prime_cache(*method_names)
        memoized_structs(method_names).each do |struct|
          if struct.arity == 0
            __send__(struct.memoized_method)
          else
            instance_variable_set(struct.ivar, {})
          end
        end
      end

      def flush_cache(*method_names)
        memoized_structs(method_names).each do |struct|
          remove_instance_variable(struct.ivar) if instance_variable_defined?(struct.ivar)
        end
      end
    end

    MemoizedMethod = Struct.new(:memoized_method, :ivar, :arity, :expires_in)

    def all_memoized_structs
      @all_memoized_structs ||= begin
                                  structs = memoized_methods.dup

                                  # Collect the memoized_methods of ancestors in ancestor order
                                  # unless we already have it since self or parents could be overriding
                                  # an ancestor method.
                                  ancestors.grep(Memoist).each do |ancestor|
                                    ancestor.memoized_methods.each do |m|
                                      structs << m unless structs.any? { |am| am.memoized_method == m.memoized_method }
                                    end
                                  end
                                  structs
                                end
    end

    def clear_structs
      @all_memoized_structs = nil
    end

    def memoize(*method_names)
      options = method_names.last.is_a?(Hash) ? method_names.pop : {}
      expires_in = options[:expires_in]

      identifier = method_names.pop[:identifier] if method_names.last.is_a?(Hash)

      method_names.each do |method_name|
        unmemoized_method = Memoist.unmemoized_method_for(method_name, identifier)
        memoized_ivar = Memoist.memoized_ivar_for(method_name, identifier)

        Memoist.memoist_eval(self) do
          include InstanceMethods

          if method_defined?(unmemoized_method)
            warn "Already memoized #{method_name}"
            return
          end
          alias_method unmemoized_method, method_name

          mm = MemoizedMethod.new(method_name, memoized_ivar, instance_method(method_name).arity, expires_in)
          memoized_methods << mm

          if mm.arity == 0

            # define a method like this;

            # def mime_type(reload=true)
            #   current_time = Time.now
            #   expiration_time = instance_variable_defined?(:@_expiration_memoized_mine_type) ? @_expiration_memoized_mine_type : nil
            #   skip_cache = reload || !instance_variable_defined?("@_memoized_mime_type") || (expiration_time && current_time > expiration_time)
            #   set_cache = skip_cache && !frozen?
            #
            #   if skip_cache
            #     value = _unmemoized_mime_type
            #   else
            #     value = @_memoized_mime_type
            #   end
            #
            #   if set_cache
            #     @_memoized_mime_type = value
            #     @_memoized_mime_type = current_time + mm.expires_in if mm.expires_in
            #   end
            #
            #   value
            # end

            trimmed_ivar = memoized_ivar[2..]
            expires_in_code = mm.expires_in ? "@_expiration_#{trimmed_ivar} = current_time + #{mm.expires_in};" : ''
            module_eval <<-EOS, __FILE__, __LINE__ + 1
              def #{method_name}(reload = false)
                current_time = Time.now
                expiration_time = instance_variable_defined?(:@_expiration_#{trimmed_ivar}) ? @_expiration_#{trimmed_ivar} : nil
                skip_cache = reload || !instance_variable_defined?("#{memoized_ivar}") || (expiration_time && current_time > expiration_time)
                set_cache = skip_cache && !frozen?

                if skip_cache
                  value = #{unmemoized_method}
                else
                  value = #{memoized_ivar}
                end

                if set_cache
                  #{memoized_ivar} = value
                  #{expires_in_code}
                end
            
                value
              end
            EOS
          else

            # define a method like this;

            # def mime_type(*args)
            #   current_time = Time.now
            #   cache = @_memoized_mime_type ||= {}
            #   arg_key = args.hash
            #   expiration_ivar = "@_expiration_memoized_mime_type_#{arg_key}"
            #
            #   if !instance_variable_defined?(expiration_ivar) || current_time > instance_variable_get(expiration_ivar)
            #     cache.delete(arg_key)
            #   end
            #
            #   reload = Memoist.extract_reload!(method(:_unmemoized_mime_type), args)
            #   skip_cache = reload || !cache.has_key?(arg_key)
            #   set_cache = skip_cache && !frozen?
            #
            #   if skip_cache
            #     value = _unmemoized_mime_type(*args)
            #   else
            #     value = cache[arg_key][:value]
            #   end
            #
            #   if set_cache
            #     cache[arg_key] = { value: value }
            #     instance_variable_set(expiration_ivar, current_time + mm.expires_in) if mm.expires_in
            #   end
            #
            #   value
            # end

            trimmed_ivar = memoized_ivar[2..]
            module_eval <<-EOS, __FILE__, __LINE__ + 1
              def #{method_name}(*args)
                current_time = Time.now
                cache = #{memoized_ivar} ||= {}
                arg_key = args.hash.abs
                expiration_ivar = "@_expiration_#{trimmed_ivar}_\#{arg_key}"
            
                if !instance_variable_defined?(expiration_ivar) || current_time > instance_variable_get(expiration_ivar)
                  cache.delete(arg_key)
                end
            
                reload = Memoist.extract_reload!(method(#{unmemoized_method.inspect}), args)
                skip_cache = reload || !cache.has_key?(arg_key)
                set_cache = skip_cache && !frozen?

                if skip_cache
                  value = #{unmemoized_method}(*args)
                else
                  value = cache[arg_key][:value]
                end
            
                if set_cache
                  cache[arg_key] = { value: value }
                  #{'instance_variable_set(expiration_ivar, current_time + ' + mm.expires_in.to_s + ')' if mm.expires_in}
                end
            
                value
              end
            EOS
          end

          if private_method_defined?(unmemoized_method)
            private method_name
          elsif protected_method_defined?(unmemoized_method)
            protected method_name
          end
        end
      end
      # return a chainable method_name symbol if we can
      method_names.length == 1 ? method_names.first : method_names
    end
  end
end